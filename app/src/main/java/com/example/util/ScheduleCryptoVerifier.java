package com.example.util;

import android.util.Base64;

import com.example.model.ClassSchedule;
import com.example.model.ScheduleSyncModels.EncryptedScheduleEnvelope;
import com.example.model.ScheduleSyncModels.ScheduleIndexManifest;
import com.example.model.ScheduleSyncModels.SectionIndexEntry;
import com.example.model.ScheduleSyncModels.SectionSchedulePackage;
import com.example.model.ScheduleSyncModels.SyncedClassEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cryptographic verifier, decryptor, and strict schema validator for public GitHub
 * section schedule manifests and encrypted section packages.
 *
 * Security model:
 * 1. Signature-before-decrypt (SHA256withECDSA over canonical metadata + ciphertext + SHA-256).
 * 2. Envelope confidentiality via AES-256-GCM (deterministic domain-derived key for zero-friction
 *    public repo encapsulation, or PBKDF2WithHmacSHA256 cohort passphrase mode).
 * 3. SHA-256 checksum verification on both encrypted envelope bytes and decrypted plaintext JSON.
 * 4. Strict structural validation and control-character sanitization before touching Room.
 */
public final class ScheduleCryptoVerifier {

    /**
     * Pinned X.509 SubjectPublicKeyInfo (Base64) ECDSA P-256 maintainer public key.
     * Maintainers generate their offline ECDSA keypair via tools/ZenSchedulePublisher.java
     * and either embed the public key here for class builds or set it in Configure Repo.
     * Private signing keys must NEVER be included inside the Android application.
     */
    public static final String DEFAULT_PUBLIC_KEY_SPKI_BASE64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8K/cvMnY68FBdT3+zAuHMGFQaXel9oX6SxiBR+LW/ge0aR1EcJrg19uy2+BG1QdOub/vKDFSyZetAEui5ZNtmA==";

    private static final String EC_ALGORITHM = "EC";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int MAX_CLASSES_PER_SECTION = 40;

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "^(\\d{1,2}):(\\d{2})(?:\\s*([APap][Mm]))?$"
    );

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ScheduleCryptoVerifier() {
    }

    public static String sha256Hex(byte[] data) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data != null ? data : new byte[0]);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format(Locale.US, "%02x", b & 0xFF));
        }
        return hex.toString();
    }

    public static boolean constantTimeEqualsHex(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ba = a.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ba, bb);
    }

    public static boolean isValidPublicKeySpki(String base64Spki) {
        if (base64Spki == null || base64Spki.trim().isEmpty()) {
            return false;
        }
        try {
            decodePublicKey(base64Spki);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getPublicKeyFingerprintShort(String base64Spki) {
        if (!isValidPublicKeySpki(base64Spki)) {
            return "Not configured";
        }
        try {
            byte[] spkiBytes = Base64.decode(sanitizeBase64(base64Spki), Base64.DEFAULT);
            String fullHex = sha256Hex(spkiBytes);
            return fullHex.substring(0, 12).toUpperCase(Locale.US);
        } catch (Exception e) {
            return "Invalid key";
        }
    }

    public static PublicKey decodePublicKey(String base64Spki) throws GeneralSecurityException {
        if (base64Spki == null || base64Spki.trim().isEmpty()) {
            throw new GeneralSecurityException("Maintainer public verification key is missing");
        }
        byte[] keyBytes = Base64.decode(sanitizeBase64(base64Spki), Base64.DEFAULT);
        KeyFactory kf = KeyFactory.getInstance(EC_ALGORITHM);
        return kf.generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    public static PrivateKey decodePrivateKey(String base64Pkcs8) throws GeneralSecurityException {
        byte[] keyBytes = Base64.decode(sanitizeBase64(base64Pkcs8), Base64.DEFAULT);
        KeyFactory kf = KeyFactory.getInstance(EC_ALGORITHM);
        return kf.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    public static String buildIndexCanonicalString(ScheduleIndexManifest manifest) {
        StringBuilder sectionsBuilder = new StringBuilder();
        List<SectionIndexEntry> entries = manifest.getSections();
        for (int i = 0; i < entries.size(); i++) {
            SectionIndexEntry e = entries.get(i);
            if (i > 0) sectionsBuilder.append(",");
            sectionsBuilder.append(safe(e.sectionId))
                    .append(":")
                    .append(safe(e.latestVersion))
                    .append(":")
                    .append(safe(e.packagePath))
                    .append(":")
                    .append(safe(e.packageSha256).toLowerCase(Locale.ROOT));
        }
        return "ZEN-INDEX-V1|"
                + manifest.schemaVersion
                + "|" + safe(manifest.repoName)
                + "|" + safe(manifest.generatedAt)
                + "|" + sectionsBuilder;
    }

    public static String buildEnvelopeCanonicalString(EncryptedScheduleEnvelope envelope) {
        return safe(envelope.format)
                + "|" + safe(envelope.sectionId)
                + "|" + safe(envelope.version)
                + "|" + safe(envelope.plaintextSha256).toLowerCase(Locale.ROOT)
                + "|" + safe(envelope.iv)
                + "|" + safe(envelope.salt)
                + "|" + safe(envelope.payload);
    }

    public static boolean verifySignature(String canonicalMessage, String base64Signature, String publicKeySpkiBase64) {
        if (canonicalMessage == null || base64Signature == null || base64Signature.trim().isEmpty()) {
            return false;
        }
        try {
            PublicKey publicKey = decodePublicKey(publicKeySpkiBase64);
            byte[] sigBytes = Base64.decode(sanitizeBase64(base64Signature), Base64.DEFAULT);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(canonicalMessage.getBytes(StandardCharsets.UTF_8));
            return signature.verify(sigBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public static String signCanonical(String canonicalMessage, PrivateKey privateKey) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(canonicalMessage.getBytes(StandardCharsets.UTF_8));
        byte[] sigBytes = signature.sign();
        return Base64.encodeToString(sigBytes, Base64.NO_WRAP);
    }

    public static ScheduleIndexManifest verifyAndParseIndex(String indexJson, String publicKeySpkiBase64)
            throws SecurityException {
        if (indexJson == null || indexJson.trim().isEmpty()) {
            throw new SecurityException("Empty schedule index response");
        }
        ScheduleIndexManifest manifest;
        try {
            manifest = GSON.fromJson(indexJson, ScheduleIndexManifest.class);
        } catch (Exception e) {
            throw new SecurityException("Malformed schedule index JSON");
        }
        if (manifest == null || manifest.schemaVersion != 1) {
            throw new SecurityException("Unsupported schedule index schema version");
        }
        if (!isValidPublicKeySpki(publicKeySpkiBase64)) {
            throw new SecurityException("Maintainer public key is not configured");
        }
        String canonical = buildIndexCanonicalString(manifest);
        if (!verifySignature(canonical, manifest.signature, publicKeySpkiBase64)) {
            throw new SecurityException("Index signature verification failed");
        }
        for (SectionIndexEntry entry : manifest.getSections()) {
            if (!isSafeRelativePackagePath(entry.packagePath)) {
                throw new SecurityException("Unsafe section package path detected in manifest");
            }
        }
        return manifest;
    }

    public static VerifiedPackageResult verifyAndDecryptPackage(
            String envelopeRawJson,
            String expectedSectionId,
            String expectedPackageSha256,
            String publicKeySpkiBase64,
            String optionalPassphrase) throws SecurityException {

        if (envelopeRawJson == null || envelopeRawJson.trim().isEmpty()) {
            throw new SecurityException("Empty section schedule package");
        }
        if (!isValidPublicKeySpki(publicKeySpkiBase64)) {
            throw new SecurityException("Maintainer public key is not configured");
        }

        try {
            byte[] rawBytes = envelopeRawJson.getBytes(StandardCharsets.UTF_8);
            if (expectedPackageSha256 != null && !expectedPackageSha256.trim().isEmpty()) {
                String actualPackageSha = sha256Hex(rawBytes);
                if (!constantTimeEqualsHex(expectedPackageSha256, actualPackageSha)) {
                    throw new SecurityException("Package file SHA-256 checksum mismatch");
                }
            }

            EncryptedScheduleEnvelope envelope = GSON.fromJson(envelopeRawJson, EncryptedScheduleEnvelope.class);
            if (envelope == null) {
                throw new SecurityException("Malformed encrypted schedule envelope");
            }
            String format = safe(envelope.format);
            if (!EncryptedScheduleEnvelope.FORMAT_STANDARD.equals(format)
                    && !EncryptedScheduleEnvelope.FORMAT_PASSPHRASE.equals(format)) {
                throw new SecurityException("Unsupported schedule package format: " + format);
            }
            if (expectedSectionId != null && !expectedSectionId.trim().isEmpty()
                    && !expectedSectionId.trim().equalsIgnoreCase(safe(envelope.sectionId))) {
                throw new SecurityException("Section ID mismatch in package envelope");
            }

            // Signature check MUST pass before decrypting untrusted ciphertext
            String canonical = buildEnvelopeCanonicalString(envelope);
            if (!verifySignature(canonical, envelope.signature, publicKeySpkiBase64)) {
                throw new SecurityException("Section package ECDSA signature invalid");
            }

            byte[] ivBytes = Base64.decode(sanitizeBase64(envelope.iv), Base64.DEFAULT);
            byte[] cipherBytes = Base64.decode(sanitizeBase64(envelope.payload), Base64.DEFAULT);
            if (ivBytes.length != GCM_IV_BYTES || cipherBytes.length == 0) {
                throw new SecurityException("Corrupted encryption parameters in package");
            }

            SecretKey aesKey;
            if (EncryptedScheduleEnvelope.FORMAT_PASSPHRASE.equals(format)) {
                if (optionalPassphrase == null || optionalPassphrase.trim().isEmpty()) {
                    throw new SecurityException("Section package requires cohort passphrase in Settings");
                }
                byte[] saltBytes = Base64.decode(sanitizeBase64(envelope.salt), Base64.DEFAULT);
                aesKey = derivePassphraseAesKey(optionalPassphrase.trim(), saltBytes);
            } else {
                aesKey = deriveStandardEnvelopeAesKey(envelope.sectionId, envelope.version, publicKeySpkiBase64);
            }

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, ivBytes));
            byte[] plaintextBytes = cipher.doFinal(cipherBytes);

            String computedPlaintextSha = sha256Hex(plaintextBytes);
            if (!constantTimeEqualsHex(envelope.plaintextSha256, computedPlaintextSha)) {
                throw new SecurityException("Decrypted payload SHA-256 verification failed");
            }

            String plaintextJson = new String(plaintextBytes, StandardCharsets.UTF_8);
            SectionSchedulePackage pkg = GSON.fromJson(plaintextJson, SectionSchedulePackage.class);
            SectionSchedulePackage validated = validateAndNormalizePackage(pkg, expectedSectionId, envelope.version);
            if ((validated.changelog == null || validated.changelog.trim().isEmpty())
                    && envelope.changelog != null) {
                validated.changelog = sanitizeText(envelope.changelog, 240);
            }

            return new VerifiedPackageResult(validated, computedPlaintextSha);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("Unable to verify and decrypt schedule package: " + e.getMessage());
        }
    }

    /**
     * Encrypts and signs a plaintext SectionSchedulePackage JSON into a verifiable EncryptedScheduleEnvelope.
     */
    public static EncryptedScheduleEnvelope createSignedEnvelope(
            SectionSchedulePackage pkg,
            PrivateKey maintainerPrivateKey,
            String maintainerPublicKeySpkiBase64,
            String optionalPassphrase) throws GeneralSecurityException {

        String plaintextJson = GSON.toJson(pkg);
        byte[] plaintextBytes = plaintextJson.getBytes(StandardCharsets.UTF_8);
        String plaintextSha = sha256Hex(plaintextBytes);

        byte[] ivBytes = new byte[GCM_IV_BYTES];
        new SecureRandom().nextBytes(ivBytes);

        EncryptedScheduleEnvelope envelope = new EncryptedScheduleEnvelope();
        envelope.sectionId = safe(pkg.sectionId);
        envelope.version = safe(pkg.version);
        envelope.changelog = sanitizeText(pkg.changelog, 240);
        envelope.iv = Base64.encodeToString(ivBytes, Base64.NO_WRAP);
        envelope.plaintextSha256 = plaintextSha;

        SecretKey aesKey;
        if (optionalPassphrase != null && !optionalPassphrase.trim().isEmpty()) {
            byte[] saltBytes = new byte[16];
            new SecureRandom().nextBytes(saltBytes);
            envelope.format = EncryptedScheduleEnvelope.FORMAT_PASSPHRASE;
            envelope.salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP);
            aesKey = derivePassphraseAesKey(optionalPassphrase.trim(), saltBytes);
        } else {
            envelope.format = EncryptedScheduleEnvelope.FORMAT_STANDARD;
            envelope.salt = "";
            aesKey = deriveStandardEnvelopeAesKey(envelope.sectionId, envelope.version, maintainerPublicKeySpkiBase64);
        }

        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, ivBytes));
        byte[] ciphertext = cipher.doFinal(plaintextBytes);
        envelope.payload = Base64.encodeToString(ciphertext, Base64.NO_WRAP);

        String canonical = buildEnvelopeCanonicalString(envelope);
        envelope.signature = signCanonical(canonical, maintainerPrivateKey);
        return envelope;
    }

    public static SectionSchedulePackage validateAndNormalizePackage(
            SectionSchedulePackage rawPkg,
            String expectedSectionId,
            String expectedVersion) throws SecurityException {
        if (rawPkg == null) {
            throw new SecurityException("Null decrypted schedule package");
        }
        if (expectedSectionId != null && !expectedSectionId.trim().isEmpty()
                && !expectedSectionId.trim().equalsIgnoreCase(safe(rawPkg.sectionId))) {
            throw new SecurityException("Decrypted section ID mismatch");
        }
        List<SyncedClassEntry> rawClasses = rawPkg.getClasses();
        if (rawClasses.size() > MAX_CLASSES_PER_SECTION) {
            throw new SecurityException("Section schedule exceeds safe slot limit (" + MAX_CLASSES_PER_SECTION + ")");
        }

        SectionSchedulePackage clean = new SectionSchedulePackage();
        clean.sectionId = sanitizeText(rawPkg.sectionId, 64);
        clean.institute = sanitizeText(rawPkg.institute, 100);
        clean.department = sanitizeText(rawPkg.department, 100);
        clean.semester = sanitizeText(rawPkg.semester, 40);
        clean.section = sanitizeText(rawPkg.section, 40);
        clean.version = sanitizeText(!safe(rawPkg.version).isEmpty() ? rawPkg.version : expectedVersion, 24);
        clean.changelog = sanitizeText(rawPkg.changelog, 240);

        List<SyncedClassEntry> validatedClasses = new ArrayList<>();
        Set<String> seenSlotKeys = new HashSet<>();

        for (int i = 0; i < rawClasses.size(); i++) {
            SyncedClassEntry entry = rawClasses.get(i);
            if (entry == null) {
                throw new SecurityException("Null schedule item at index " + i);
            }

            String canonicalDay = normalizeWeekday(entry.dayOfWeek);
            if (canonicalDay == null) {
                throw new SecurityException("Invalid weekday in schedule slot: " + entry.dayOfWeek);
            }

            String subject = sanitizeText(entry.subjectName, 80);
            if (subject.isEmpty()) {
                throw new SecurityException("Empty subject name in schedule slot");
            }

            int startMin = parseStrictTimeMinutes(entry.startTime);
            int endMin = parseStrictTimeMinutes(entry.endTime);
            if (startMin < 0 || endMin < 0 || endMin <= startMin) {
                throw new SecurityException("Invalid time range for " + subject + ": "
                        + entry.startTime + " - " + entry.endTime);
            }

            if (entry.creditHours < 1 || entry.creditHours > 6) {
                throw new SecurityException("Invalid credit hours (" + entry.creditHours + ") for " + subject);
            }

            String normalizedType = "Lab".equalsIgnoreCase(safe(entry.classType)) ? "Lab" : "Theory";
            String normalizedRoom = sanitizeText(entry.roomNumber, 40);
            String startNormalized = sanitizeText(entry.startTime, 20);
            String endNormalized = sanitizeText(entry.endTime, 20);

            String slotKey = sanitizeSlotKey(entry.slotKey);
            if (slotKey.isEmpty()) {
                slotKey = generateDeterministicSlotKey(canonicalDay, subject, normalizedType, startNormalized);
            }
            if (!seenSlotKeys.add(slotKey)) {
                slotKey = slotKey + "_" + i;
                seenSlotKeys.add(slotKey);
            }

            SyncedClassEntry normalized = new SyncedClassEntry();
            normalized.slotKey = slotKey;
            normalized.dayOfWeek = canonicalDay;
            normalized.subjectName = subject;
            normalized.startTime = startNormalized;
            normalized.endTime = endNormalized;
            normalized.creditHours = entry.creditHours;
            normalized.classType = normalizedType;
            normalized.roomNumber = normalizedRoom;
            validatedClasses.add(normalized);
        }

        clean.classes = validatedClasses;
        return clean;
    }

    public static boolean isSafeRelativePackagePath(String path) {
        if (path == null) return false;
        String trimmed = path.trim();
        if (trimmed.isEmpty() || trimmed.length() > 180) return false;
        if (trimmed.startsWith("/") || trimmed.contains("\\") || trimmed.contains("..") || trimmed.contains("://")) {
            return false;
        }
        return trimmed.matches("^[A-Za-z0-9_./-]+$");
    }

    private static SecretKey deriveStandardEnvelopeAesKey(String sectionId, String version, String spkiBase64)
            throws GeneralSecurityException {
        byte[] spkiBytes = Base64.decode(sanitizeBase64(spkiBase64), Base64.DEFAULT);
        String keyMaterial = "ZEN-SCHED-ENVELOPE-V1|"
                + safe(sectionId).toLowerCase(Locale.ROOT)
                + "|" + safe(version).toUpperCase(Locale.ROOT)
                + "|" + sha256Hex(spkiBytes);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static SecretKey derivePassphraseAesKey(String passphrase, byte[] saltBytes)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), saltBytes, PBKDF2_ITERATIONS, 256);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] derived = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(derived, "AES");
    }

    private static String normalizeWeekday(String rawDay) {
        if (rawDay == null) return null;
        switch (rawDay.trim().toLowerCase(Locale.ROOT)) {
            case "monday":
            case "mon":
                return "Monday";
            case "tuesday":
            case "tue":
                return "Tuesday";
            case "wednesday":
            case "wed":
                return "Wednesday";
            case "thursday":
            case "thu":
                return "Thursday";
            case "friday":
            case "fri":
                return "Friday";
            default:
                return null;
        }
    }

    private static int parseStrictTimeMinutes(String timeStr) {
        if (timeStr == null) return -1;
        Matcher m = TIME_PATTERN.matcher(timeStr.trim());
        if (!m.matches()) return -1;
        try {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            String amPm = m.group(3);
            if (minute < 0 || minute > 59) return -1;
            if (amPm != null) {
                if (hour < 1 || hour > 12) return -1;
                if (amPm.equalsIgnoreCase("PM") && hour != 12) hour += 12;
                if (amPm.equalsIgnoreCase("AM") && hour == 12) hour = 0;
            } else {
                if (hour < 0 || hour > 23) return -1;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String generateDeterministicSlotKey(String day, String subject, String type, String start) {
        String raw = day.toLowerCase(Locale.ROOT) + "_"
                + subject.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_") + "_"
                + type.toLowerCase(Locale.ROOT) + "_"
                + start.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        return raw.length() > 64 ? raw.substring(0, 64) : raw;
    }

    private static String sanitizeSlotKey(String key) {
        if (key == null) return "";
        String clean = key.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }

    private static String sanitizeText(String input, int maxLen) {
        if (input == null) return "";
        String cleaned = input
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replace("<", "")
                .replace(">", "")
                .trim();
        if (cleaned.length() > maxLen) {
            return cleaned.substring(0, maxLen).trim();
        }
        return cleaned;
    }

    private static String sanitizeBase64(String b64) {
        return b64 != null ? b64.replaceAll("\\s+", "") : "";
    }

    private static String safe(String s) {
        return s != null ? s.trim() : "";
    }

    public static class VerifiedPackageResult {
        public final SectionSchedulePackage schedulePackage;
        public final String plaintextSha256;

        public VerifiedPackageResult(SectionSchedulePackage schedulePackage, String plaintextSha256) {
            this.schedulePackage = schedulePackage;
            this.plaintextSha256 = plaintextSha256;
        }
    }
}
