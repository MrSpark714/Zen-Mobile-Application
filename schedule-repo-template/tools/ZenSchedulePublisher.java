import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
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
 * ECDSA P-256 keypairs, encrypt & sign section schedules (.zensched), and sign index.json.
 *
 * Usage:
 *   javac ZenSchedulePublisher.java
 *   java ZenSchedulePublisher keygen ./keys
 *   java ZenSchedulePublisher package ./cs_sem5_a_raw.json ./sections/cs_sem5_a.zensched ./keys/private.key ./keys/public.key [optional_passphrase]
 */
public class ZenSchedulePublisher {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }
        switch (args[0]) {
            case "keygen":
                String outDir = args.length > 1 ? args[1] : ".";
                generateKeyPair(new File(outDir));
                break;
            case "package":
                if (args.length < 5) {
                    printUsage();
                    return;
                }
                String passphrase = args.length >= 6 ? args[5] : "";
                packageSection(new File(args[1]), new File(args[2]), new File(args[3]), new File(args[4]), passphrase);
                break;
            case "sign-canonical":
                // Utility to sign pre-built canonical index string cleanly
                if (args.length < 3) {
                    printUsage();
                    return;
                }
                PrivateKey priv = readPrivateKey(new File(args[2]));
                System.out.println(signCanonical(args[1], priv));
                break;
            default:
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("ZEN Schedule Publisher CLI (Zero external dependencies)");
        System.out.println("  keygen <output_dir>");
        System.out.println("  package <raw_section.json> <out_section.zensched> <private.key> <public.key> [passphrase]");
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

        System.out.println("Generated ECDSA P-256 keypair in " + dir.getAbsolutePath());
        System.out.println("KEEP private.key SECRET AND OFFLINE (never commit private.key to GitHub!).");
        System.out.println("Public Key (Base64) to configure in ZEN app or ScheduleCryptoVerifier:");
        System.out.println(pubB64);
    }

    private static void packageSection(
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

        System.out.println("Created verified encrypted package: " + outEnvelopeFile.getPath());
        System.out.println("package_sha256 (for index.json): " + sha256Hex(outBytes));
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

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
