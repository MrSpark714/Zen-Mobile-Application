package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Local preference & state manager for battery-conscious section schedule syncing.
 * Stores cooldown timestamps, HTTP ETag headers, installed/skipped version tags,
 * strictly validated HTTPS repository base URL, and Keystore-encrypted optional passphrase.
 */
public final class ScheduleSyncPreferences {

    private static final String PREFS_NAME = "zen_schedule_sync_prefs";
    private static final String KEYSTORE_ALIAS = "zen_schedule_sync_aes_key";

    private static final String KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled";
    private static final String KEY_LAST_CHECK_MILLIS = "last_check_millis";
    private static final String KEY_INDEX_ETAG = "index_etag";
    private static final String KEY_MATCHED_SECTION_ID = "matched_section_id";
    private static final String KEY_MATCHED_SECTION_LABEL = "matched_section_label";
    private static final String KEY_APPLIED_VERSION = "applied_version";
    private static final String KEY_SKIPPED_VERSION = "skipped_version";
    private static final String KEY_LAST_SYNC_SUMMARY = "last_sync_summary";
    private static final String KEY_REPO_BASE_URL = "repo_base_url";
    private static final String KEY_PUBLIC_KEY_OVERRIDE = "public_key_override";
    private static final String KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase";

    public static final long CHECK_COOLDOWN_MS = 6L * 60L * 60L * 1000L; // 6 hours
    public static final String DEFAULT_REPO_BASE_URL =
            "https://raw.githubusercontent.com/MrSpark714/Zen-Mobile-Application/main/schedules/";

    private ScheduleSyncPreferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isAutoSyncEnabled(Context context) {
        if (context == null) return true;
        return prefs(context).getBoolean(KEY_AUTO_SYNC_ENABLED, true);
    }

    public static void setAutoSyncEnabled(Context context, boolean enabled) {
        if (context == null) return;
        prefs(context).edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply();
    }

    public static long getLastCheckMillis(Context context) {
        if (context == null) return 0L;
        return prefs(context).getLong(KEY_LAST_CHECK_MILLIS, 0L);
    }

    public static void setLastCheckMillis(Context context, long millis) {
        if (context == null) return;
        prefs(context).edit().putLong(KEY_LAST_CHECK_MILLIS, millis).apply();
    }

    public static boolean isCooldownElapsed(Context context, long nowMillis) {
        long lastCheck = getLastCheckMillis(context);
        return lastCheck <= 0L || (nowMillis - lastCheck) >= CHECK_COOLDOWN_MS;
    }

    public static String getIndexETag(Context context) {
        if (context == null) return "";
        return prefs(context).getString(KEY_INDEX_ETAG, "");
    }

    public static void setIndexETag(Context context, String etag) {
        if (context == null) return;
        prefs(context).edit().putString(KEY_INDEX_ETAG, etag != null ? etag.trim() : "").apply();
    }

    public static String getMatchedSectionId(Context context) {
        if (context == null) return "";
        return prefs(context).getString(KEY_MATCHED_SECTION_ID, "");
    }

    public static String getMatchedSectionLabel(Context context) {
        if (context == null) return "";
        return prefs(context).getString(KEY_MATCHED_SECTION_LABEL, "");
    }

    public static void setMatchedSection(Context context, String sectionId, String label) {
        if (context == null) return;
        prefs(context).edit()
                .putString(KEY_MATCHED_SECTION_ID, sectionId != null ? sectionId.trim() : "")
                .putString(KEY_MATCHED_SECTION_LABEL, label != null ? label.trim() : "")
                .apply();
    }

    public static String getAppliedVersion(Context context) {
        if (context == null) return "";
        return prefs(context).getString(KEY_APPLIED_VERSION, "");
    }

    public static void setAppliedVersion(Context context, String version) {
        if (context == null) return;
        prefs(context).edit().putString(KEY_APPLIED_VERSION, version != null ? version.trim() : "").apply();
    }

    public static String getSkippedVersion(Context context) {
        if (context == null) return "";
        return prefs(context).getString(KEY_SKIPPED_VERSION, "");
    }

    public static void setSkippedVersion(Context context, String version) {
        if (context == null) return;
        prefs(context).edit().putString(KEY_SKIPPED_VERSION, version != null ? version.trim() : "").apply();
    }

    public static String getLastSyncSummary(Context context) {
        if (context == null) return "";
        return prefs(context).getString(KEY_LAST_SYNC_SUMMARY, "");
    }

    public static void setLastSyncSummary(Context context, String summary) {
        if (context == null) return;
        prefs(context).edit().putString(KEY_LAST_SYNC_SUMMARY, summary != null ? summary.trim() : "").apply();
    }

    public static String getRepoBaseUrl(Context context) {
        if (context == null) return DEFAULT_REPO_BASE_URL;
        String stored = prefs(context).getString(KEY_REPO_BASE_URL, "");
        if (stored == null || stored.trim().isEmpty()) {
            return DEFAULT_REPO_BASE_URL;
        }
        return normalizeSafeHttpsBaseUrl(stored) != null ? normalizeSafeHttpsBaseUrl(stored) : DEFAULT_REPO_BASE_URL;
    }

    public static boolean setRepoBaseUrl(Context context, String rawUrl) {
        if (context == null) return false;
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            prefs(context).edit()
                    .remove(KEY_REPO_BASE_URL)
                    .remove(KEY_INDEX_ETAG)
                    .remove(KEY_LAST_CHECK_MILLIS)
                    .apply();
            return true;
        }
        String normalized = normalizeSafeHttpsBaseUrl(rawUrl);
        if (normalized == null) {
            return false;
        }
        prefs(context).edit()
                .putString(KEY_REPO_BASE_URL, normalized)
                .remove(KEY_INDEX_ETAG)
                .remove(KEY_LAST_CHECK_MILLIS)
                .apply();
        return true;
    }

    /**
     * Validates that a repository base URL strictly uses HTTPS, rejects loopback/private/local hosts,
     * and ends with a trailing slash.
     */
    public static String normalizeSafeHttpsBaseUrl(String rawUrl) {
        if (rawUrl == null) return null;
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || !scheme.equalsIgnoreCase("https")) {
                return null;
            }
            if (host == null || host.trim().isEmpty()) {
                return null;
            }
            String h = host.trim().toLowerCase(Locale.ROOT);
            if (h.equals("localhost")
                    || h.endsWith(".local")
                    || h.equals("0.0.0.0")
                    || h.equals("::1")
                    || h.startsWith("127.")
                    || h.startsWith("10.")
                    || h.startsWith("192.168.")
                    || h.startsWith("169.254.")
                    || h.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
                return null;
            }
            if (!trimmed.endsWith("/")) {
                trimmed = trimmed + "/";
            }
            return trimmed;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getEffectivePublicKey(Context context) {
        if (context != null) {
            String override = prefs(context).getString(KEY_PUBLIC_KEY_OVERRIDE, "");
            if (override != null && !override.trim().isEmpty()) {
                return override.trim();
            }
        }
        return ScheduleCryptoVerifier.DEFAULT_PUBLIC_KEY_SPKI_BASE64;
    }

    public static boolean setPublicKeyOverride(Context context, String spkiBase64) {
        if (context == null) return false;
        if (spkiBase64 == null || spkiBase64.trim().isEmpty()) {
            prefs(context).edit().remove(KEY_PUBLIC_KEY_OVERRIDE).apply();
            return true;
        }
        String clean = spkiBase64.trim();
        if (!ScheduleCryptoVerifier.isValidPublicKeySpki(clean)) {
            return false;
        }
        prefs(context).edit().putString(KEY_PUBLIC_KEY_OVERRIDE, clean).apply();
        return true;
    }

    public static void setOptionalPassphrase(Context context, String passphrase) {
        if (context == null) return;
        if (passphrase == null || passphrase.trim().isEmpty()) {
            prefs(context).edit().remove(KEY_ENCRYPTED_PASSPHRASE).apply();
            return;
        }
        try {
            SecretKey key = getOrCreateKeystoreAesKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(passphrase.trim().getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            prefs(context).edit()
                    .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(combined, Base64.NO_WRAP))
                    .apply();
        } catch (Exception ignored) {
            // Do not persist plaintext passphrase if AndroidKeyStore is unavailable
        }
    }

    public static String getOptionalPassphrase(Context context) {
        if (context == null) return "";
        String stored = prefs(context).getString(KEY_ENCRYPTED_PASSPHRASE, "");
        if (stored == null || stored.isEmpty()) return "";
        try {
            byte[] combined = Base64.decode(stored, Base64.DEFAULT);
            if (combined.length <= 12) return "";
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
            SecretKey key = getOrCreateKeystoreAesKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static void resetCooldownForProfileChange(Context context) {
        if (context == null) return;
        prefs(context).edit()
                .remove(KEY_LAST_CHECK_MILLIS)
                .remove(KEY_INDEX_ETAG)
                .apply();
    }

    private static SecretKey getOrCreateKeystoreAesKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEYSTORE_ALIAS)) {
            return (SecretKey) ks.getKey(KEYSTORE_ALIAS, null);
        }
        KeyGenerator keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGen.init(new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return keyGen.generateKey();
    }
}
