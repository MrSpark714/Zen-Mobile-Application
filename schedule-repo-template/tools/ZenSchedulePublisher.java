import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Zero-dependency JDK CLI tool for university schedule maintainers to generate
 * ECDSA P-256 keypairs, encrypt & sign section schedules (.zensched), and build/sign index.json
 * for one or multiple sections in a single command.
 *
 * Usage:
 *   javac ZenSchedulePublisher.java
 *   java ZenSchedulePublisher keygen ./offline_keys
 *   java ZenSchedulePublisher publish-all ./nfc_cs_sem5 ./schedules ./offline_keys/private.key ./offline_keys/public.key
 *   java ZenSchedulePublisher package ./raw.json ./schedules/sections/sec.zensched ./offline_keys/private.key ./offline_keys/public.key [passphrase]
 */
public class ZenSchedulePublisher {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }
        switch (args[0]) {
            case "keygen": {
                String outDir = args.length > 1 ? args[1] : ".";
                generateKeyPair(new File(outDir));
                break;
            }
            case "package": {
                if (args.length < 5) {
                    printUsage();
                    return;
                }
                String passphrase = args.length >= 6 ? args[5] : "";
                packageSection(new File(args[1]), new File(args[2]), new File(args[3]), new File(args[4]), passphrase);
                break;
            }
            case "publish-all": {
                if (args.length < 5) {
                    printUsage();
                    return;
                }
                String passphrase = args.length >= 6 ? args[5] : "";
                publishAllSections(new File(args[1]), new File(args[2]), new File(args[3]), new File(args[4]), passphrase);
                break;
            }
            case "sign-canonical": {
                if (args.length < 3) {
                    printUsage();
                    return;
                }
                PrivateKey priv = readPrivateKey(new File(args[2]));
                System.out.println(signCanonical(args[1], priv));
                break;
            }
            case "verify": {
                if (args.length < 3) {
                    printUsage();
                    return;
                }
                verifySchedulesDir(new File(args[1]), new File(args[2]));
                break;
            }
            default:
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("ZEN Schedule Publisher CLI (Zero external dependencies)");
        System.out.println("  keygen <output_dir>");
        System.out.println("  publish-all <raw_sections_dir> <out_schedules_dir> <private.key> <public.key> [passphrase]");
        System.out.println("  verify <schedules_dir> <public.key>");
        System.out.println("  package <raw_section.json> <out_section.zensched> <private.key> <public.key> [passphrase]");
        System.out.println("  sign-canonical <canonical_string> <private.key>");
    }

    private static void generateKeyPair(File dir) throws Exception {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create key directory: " + dir);
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair pair = kpg.generateKeyPair();

        String privB64 = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String pubB64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        Files.writeString(new File(dir, "private.key").toPath(), privB64, StandardCharsets.UTF_8);
        Files.writeString(new File(dir, "public.key").toPath(), pubB64, StandardCharsets.UTF_8);

        String pubFp = sha256Hex(pair.getPublic().getEncoded()).substring(0, 12).toUpperCase(Locale.US);
        System.out.println("Generated ECDSA P-256 keypair in " + dir.getAbsolutePath());
        System.out.println("KEEP private.key SECRET AND OFFLINE (never commit private.key to GitHub!).");
        System.out.println("Public Key Fingerprint (compare with App Settings -> Verification key): " + pubFp);
        System.out.println("Public Key (Base64) to configure in ZEN app or ScheduleCryptoVerifier:");
        System.out.println(pubB64);
    }

    private static void publishAllSections(
            File rawDir,
            File outSchedulesDir,
            File privateKeyFile,
            File publicKeyFile,
            String passphrase) throws Exception {

        if (!rawDir.isDirectory()) {
            throw new IllegalArgumentException("Raw sections directory does not exist: " + rawDir);
        }
        File[] rawFiles = rawDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (rawFiles == null || rawFiles.length == 0) {
            throw new IllegalArgumentException("No .json files found in: " + rawDir);
        }
        Arrays.sort(rawFiles, Comparator.comparing(File::getName));

        File sectionsOutDir = new File(outSchedulesDir, "sections");
        if (!sectionsOutDir.exists() && !sectionsOutDir.mkdirs()) {
            throw new IllegalStateException("Could not create output sections directory: " + sectionsOutDir);
        }

        PrivateKey privateKey = readPrivateKey(privateKeyFile);
        List<PublishedSectionMeta> published = new ArrayList<>();

        for (File rawFile : rawFiles) {
            String rawJson = Files.readString(rawFile.toPath(), StandardCharsets.UTF_8).trim();
            String sectionId = extractJsonString(rawJson, "section_id");
            String institute = extractJsonString(rawJson, "institute");
            String department = extractJsonString(rawJson, "department");
            String semester = extractJsonString(rawJson, "semester");
            String section = extractJsonString(rawJson, "section");
            String version = extractJsonString(rawJson, "version");

            if (sectionId.isEmpty() || version.isEmpty() || institute.isEmpty()
                    || department.isEmpty() || semester.isEmpty() || section.isEmpty()) {
                throw new IllegalArgumentException("Missing required metadata fields in " + rawFile.getName());
            }

            String relPath = "sections/" + sectionId + ".zensched";
            File outEnvelope = new File(outSchedulesDir, relPath);
            String packageSha = packageSection(rawFile, outEnvelope, privateKeyFile, publicKeyFile, passphrase);

            PublishedSectionMeta meta = new PublishedSectionMeta();
            meta.sectionId = sectionId;
            meta.institute = institute;
            meta.instituteAliases = extractJsonStringArray(rawJson, "institute_aliases");
            meta.department = department;
            meta.departmentAliases = extractJsonStringArray(rawJson, "department_aliases");
            meta.semester = semester;
            meta.semesterAliases = extractJsonStringArray(rawJson, "semester_aliases");
            meta.section = section;
            meta.sectionAliases = extractJsonStringArray(rawJson, "section_aliases");
            meta.latestVersion = version;
            meta.packagePath = relPath;
            meta.packageSha256 = packageSha;
            published.add(meta);
        }

        String repoName = "University Cohort Schedules";
        String generatedAt = Instant.now().toString();
        StringBuilder canonicalSections = new StringBuilder();
        StringBuilder sectionsJson = new StringBuilder();

        for (int i = 0; i < published.size(); i++) {
            PublishedSectionMeta m = published.get(i);
            if (i > 0) {
                canonicalSections.append(",");
                sectionsJson.append(",\n");
            }
            canonicalSections.append(m.sectionId)
                    .append(":")
                    .append(m.latestVersion)
                    .append(":")
                    .append(m.packagePath)
                    .append(":")
                    .append(m.packageSha256.toLowerCase(Locale.ROOT));

            sectionsJson.append("    {\n")
                    .append("      \"section_id\": \"").append(escapeJson(m.sectionId)).append("\",\n")
                    .append("      \"institute\": \"").append(escapeJson(m.institute)).append("\",\n")
                    .append("      \"institute_aliases\": ").append(toJsonArray(m.instituteAliases)).append(",\n")
                    .append("      \"department\": \"").append(escapeJson(m.department)).append("\",\n")
                    .append("      \"department_aliases\": ").append(toJsonArray(m.departmentAliases)).append(",\n")
                    .append("      \"semester\": \"").append(escapeJson(m.semester)).append("\",\n")
                    .append("      \"semester_aliases\": ").append(toJsonArray(m.semesterAliases)).append(",\n")
                    .append("      \"section\": \"").append(escapeJson(m.section)).append("\",\n")
                    .append("      \"section_aliases\": ").append(toJsonArray(m.sectionAliases)).append(",\n")
                    .append("      \"latest_version\": \"").append(escapeJson(m.latestVersion)).append("\",\n")
                    .append("      \"package_path\": \"").append(escapeJson(m.packagePath)).append("\",\n")
                    .append("      \"package_sha256\": \"").append(escapeJson(m.packageSha256)).append("\"\n")
                    .append("    }");
        }

        String canonicalIndex = "ZEN-INDEX-V1|1|" + repoName + "|" + generatedAt + "|" + canonicalSections;
        String indexSignature = signCanonical(canonicalIndex, privateKey);

        String indexJson = "{\n"
                + "  \"schema_version\": 1,\n"
                + "  \"repo_name\": \"" + escapeJson(repoName) + "\",\n"
                + "  \"generated_at\": \"" + escapeJson(generatedAt) + "\",\n"
                + "  \"sections\": [\n"
                + sectionsJson + "\n"
                + "  ],\n"
                + "  \"signature\": \"" + escapeJson(indexSignature) + "\"\n"
                + "}\n";

        File indexFile = new File(outSchedulesDir, "index.json");
        Files.writeString(indexFile.toPath(), indexJson, StandardCharsets.UTF_8);
        String pubB64 = Files.readString(publicKeyFile.toPath(), StandardCharsets.UTF_8).replaceAll("\\s+", "");
        String pubFp = sha256Hex(Base64.getDecoder().decode(pubB64)).substring(0, 12).toUpperCase(Locale.US);
        System.out.println("Published signed index with " + published.size() + " section(s): " + indexFile.getPath());
        System.out.println("Expected App Verification key fingerprint: " + pubFp);
    }

    private static void verifySchedulesDir(File schedulesDir, File publicKeyFile) throws Exception {
        File indexFile = new File(schedulesDir, "index.json");
        if (!indexFile.isFile()) {
            throw new IllegalArgumentException("index.json not found in " + schedulesDir.getAbsolutePath());
        }
        String pubB64 = Files.readString(publicKeyFile.toPath(), StandardCharsets.UTF_8).replaceAll("\\s+", "");
        byte[] pubBytes = Base64.getDecoder().decode(pubB64);
        PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(pubBytes));
        String pubFp = sha256Hex(pubBytes).substring(0, 12).toUpperCase(Locale.US);
        System.out.println("Checking with public.key fingerprint: " + pubFp);

        String indexJson = Files.readString(indexFile.toPath(), StandardCharsets.UTF_8);
        String repoName = extractJsonString(indexJson, "repo_name");
        String generatedAt = extractJsonString(indexJson, "generated_at");
        String signature = extractJsonString(indexJson, "signature");

        Matcher secMatcher = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL).matcher(
                extractSectionsBlock(indexJson)
        );
        StringBuilder canonicalSections = new StringBuilder();
        int count = 0;
        while (secMatcher.find()) {
            String block = secMatcher.group(1);
            String sectionId = extractJsonString(block, "section_id");
            String latestVersion = extractJsonString(block, "latest_version");
            String packagePath = extractJsonString(block, "package_path");
            String packageSha = extractJsonString(block, "package_sha256");
            if (count > 0) canonicalSections.append(",");
            canonicalSections.append(sectionId)
                    .append(":").append(latestVersion)
                    .append(":").append(packagePath)
                    .append(":").append(packageSha.toLowerCase(Locale.ROOT));
            count++;

            File pkgFile = new File(schedulesDir, packagePath);
            if (!pkgFile.isFile()) {
                System.out.println("  [FAIL] Missing package file: " + pkgFile.getPath());
            } else {
                String actualSha = sha256Hex(Files.readAllBytes(pkgFile.toPath()));
                boolean shaOk = actualSha.equalsIgnoreCase(packageSha);
                System.out.println("  " + sectionId + " (" + latestVersion + "): file SHA-256 "
                        + (shaOk ? "OK" : "MISMATCH"));
            }
        }

        String canonicalIndex = "ZEN-INDEX-V1|1|" + repoName + "|" + generatedAt + "|" + canonicalSections;
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initVerify(publicKey);
        sig.update(canonicalIndex.getBytes(StandardCharsets.UTF_8));
        boolean indexValid = sig.verify(Base64.getDecoder().decode(signature.replaceAll("\\s+", "")));
        System.out.println("index.json ECDSA signature: " + (indexValid ? "VALID" : "INVALID"));
    }

    private static String extractSectionsBlock(String json) {
        int start = json.indexOf("\"sections\"");
        if (start < 0) return "";
        int open = json.indexOf('[', start);
        int close = json.lastIndexOf(']');
        if (open < 0 || close <= open) return "";
        return json.substring(open + 1, close);
    }

    private static String packageSection(
            File rawJsonFile,
            File outEnvelopeFile,
            File privateKeyFile,
            File publicKeyFile,
            String passphrase) throws Exception {

        String rawJson = Files.readString(rawJsonFile.toPath(), StandardCharsets.UTF_8).trim();
        byte[] plaintextBytes = rawJson.getBytes(StandardCharsets.UTF_8);
        String plaintextSha = sha256Hex(plaintextBytes);

        String sectionId = extractJsonString(rawJson, "section_id");
        String version = extractJsonString(rawJson, "version");
        String changelog = extractJsonString(rawJson, "changelog");
        if (sectionId.isEmpty() || version.isEmpty()) {
            throw new IllegalArgumentException("raw_section.json must contain non-empty 'section_id' and 'version'");
        }

        PrivateKey privateKey = readPrivateKey(privateKeyFile);
        String publicKeySpkiB64 = Files.readString(publicKeyFile.toPath(), StandardCharsets.UTF_8).replaceAll("\\s+", "");

        byte[] ivBytes = new byte[12];
        new SecureRandom().nextBytes(ivBytes);
        String ivB64 = Base64.getEncoder().encodeToString(ivBytes);

        String format;
        String saltB64 = "";
        SecretKey aesKey;
        if (passphrase != null && !passphrase.trim().isEmpty()) {
            format = "ZEN-SCHED-ENC-PASSPHRASE-1";
            byte[] saltBytes = new byte[16];
            new SecureRandom().nextBytes(saltBytes);
            saltB64 = Base64.getEncoder().encodeToString(saltBytes);
            PBEKeySpec spec = new PBEKeySpec(passphrase.trim().toCharArray(), saltBytes, 100_000, 256);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            aesKey = new SecretKeySpec(skf.generateSecret(spec).getEncoded(), "AES");
        } else {
            format = "ZEN-SCHED-ENC-1";
            byte[] spkiBytes = Base64.getDecoder().decode(publicKeySpkiB64);
            String keyMaterial = "ZEN-SCHED-ENVELOPE-V1|"
                    + sectionId.toLowerCase(Locale.ROOT)
                    + "|" + version.toUpperCase(Locale.ROOT)
                    + "|" + sha256Hex(spkiBytes);
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
            aesKey = new SecretKeySpec(keyBytes, "AES");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, ivBytes));
        String payloadB64 = Base64.getEncoder().encodeToString(cipher.doFinal(plaintextBytes));

        String canonical = format + "|" + sectionId + "|" + version + "|"
                + plaintextSha.toLowerCase(Locale.ROOT) + "|" + ivB64 + "|" + saltB64 + "|" + payloadB64;
        String signatureB64 = signCanonical(canonical, privateKey);

        String outJson = "{\n"
                + "  \"format\": \"" + escapeJson(format) + "\",\n"
                + "  \"section_id\": \"" + escapeJson(sectionId) + "\",\n"
                + "  \"version\": \"" + escapeJson(version) + "\",\n"
                + "  \"changelog\": \"" + escapeJson(changelog) + "\",\n"
                + "  \"iv\": \"" + ivB64 + "\",\n"
                + "  \"salt\": \"" + saltB64 + "\",\n"
                + "  \"payload\": \"" + payloadB64 + "\",\n"
                + "  \"plaintext_sha256\": \"" + plaintextSha + "\",\n"
                + "  \"signature\": \"" + signatureB64 + "\"\n"
                + "}\n";

        if (outEnvelopeFile.getParentFile() != null) {
            outEnvelopeFile.getParentFile().mkdirs();
        }
        byte[] outBytes = outJson.getBytes(StandardCharsets.UTF_8);
        Files.write(outEnvelopeFile.toPath(), outBytes);

        String packageSha = sha256Hex(outBytes);
        System.out.println("Created verified encrypted package: " + outEnvelopeFile.getPath());
        System.out.println("package_sha256: " + packageSha);
        return packageSha;
    }

    private static PrivateKey readPrivateKey(File file) throws Exception {
        String b64 = Files.readString(file.toPath(), StandardCharsets.UTF_8).replaceAll("\\s+", "");
        byte[] bytes = Base64.getDecoder().decode(b64);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    private static String signCanonical(String canonical, PrivateKey key) throws GeneralSecurityException {
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(key);
        sig.update(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    private static String sha256Hex(byte[] data) throws GeneralSecurityException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format(Locale.US, "%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static String extractJsonString(String json, String field) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"") : "";
    }

    private static List<String> extractJsonStringArray(String json, String field) {
        List<String> result = new ArrayList<>();
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (!m.find()) return result;
        String inner = m.group(1);
        Matcher itemMatcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(inner);
        while (itemMatcher.find()) {
            result.add(itemMatcher.group(1).replace("\\\"", "\""));
        }
        return result;
    }

    private static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escapeJson(items.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class PublishedSectionMeta {
        String sectionId;
        String institute;
        List<String> instituteAliases = new ArrayList<>();
        String department;
        List<String> departmentAliases = new ArrayList<>();
        String semester;
        List<String> semesterAliases = new ArrayList<>();
        String section;
        List<String> sectionAliases = new ArrayList<>();
        String latestVersion;
        String packagePath;
        String packageSha256;
    }
}
