package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import com.example.model.UserProfile;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Hardware-backed Android Keystore AES-256-GCM encrypted local vault for personal
 * profile metadata and personal avatar photos.
 *
 * All data stays strictly in app-private internal storage (filesDir/zen_vault),
 * encrypted at rest, excluded from cloud/ADB backups, and never exposed via FileProvider.
 */
public final class SecureProfileStore {

    private static final String TAG = "SecureProfileStore";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "zen_profile_master_key_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final byte FORMAT_VERSION = 1;

    private static final String VAULT_DIR_NAME = "zen_vault";
    private static final String PROFILE_FILE_NAME = "profile.enc";
    private static final String AVATAR_FILE_NAME = "avatar.enc";

    private static final String PREFS_ZEN = "zen_prefs";
    private static final String KEY_LEGACY_USER_NAME = "dashboard_user_name";
    private static final String KEY_PROFILE_SETUP_SEEN = "profile_setup_seen_v1";

    private static final int MAX_AVATAR_DIMENSION = 512;
    private static final Gson GSON = new Gson();

    private SecureProfileStore() {
    }

    public static boolean hasSeenProfileSetup(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ZEN, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PROFILE_SETUP_SEEN, false);
    }

    public static void markProfileSetupSeen(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ZEN, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PROFILE_SETUP_SEEN, true).apply();
    }

    public static synchronized boolean saveProfile(Context context, UserProfile profile) {
        if (context == null || profile == null) return false;
        try {
            String json = GSON.toJson(profile);
            byte[] encrypted = encrypt(json.getBytes(StandardCharsets.UTF_8));
            File vaultFile = getVaultFile(context, PROFILE_FILE_NAME);
            writeBytesAtomic(vaultFile, encrypted);

            // Ensure any legacy plaintext display name is scrubbed
            SharedPreferences prefs = context.getSharedPreferences(PREFS_ZEN, Context.MODE_PRIVATE);
            if (prefs.contains(KEY_LEGACY_USER_NAME)) {
                prefs.edit().remove(KEY_LEGACY_USER_NAME).apply();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save encrypted profile", e);
            return false;
        }
    }

    public static synchronized UserProfile loadProfile(Context context) {
        if (context == null) return new UserProfile();
        try {
            File vaultFile = getVaultFile(context, PROFILE_FILE_NAME);
            if (vaultFile.exists()) {
                byte[] encrypted = readAllBytes(vaultFile);
                byte[] plaintext = decrypt(encrypted);
                String json = new String(plaintext, StandardCharsets.UTF_8);
                UserProfile loaded = GSON.fromJson(json, UserProfile.class);
                if (loaded != null) {
                    return loaded;
                }
            }

            // Migrate any legacy plaintext dashboard_user_name into encrypted storage
            SharedPreferences prefs = context.getSharedPreferences(PREFS_ZEN, Context.MODE_PRIVATE);
            String legacyName = prefs.getString(KEY_LEGACY_USER_NAME, "");
            if (legacyName != null && !legacyName.trim().isEmpty()) {
                UserProfile migrated = new UserProfile();
                migrated.setFullName(legacyName.trim());
                saveProfile(context, migrated);
                return migrated;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load encrypted profile", e);
        }
        return new UserProfile();
    }

    public static synchronized boolean saveAvatarFromUri(Context context, Uri imageUri) {
        if (context == null || imageUri == null) return false;
        try {
            Bitmap processed = decodeAndOrientAvatar(context, imageUri);
            if (processed == null) {
                return false;
            }
            return saveAvatarBitmap(context, processed);
        } catch (Exception e) {
            Log.e(TAG, "Failed to process and encrypt avatar image", e);
            return false;
        }
    }

    public static synchronized boolean saveAvatarBitmap(Context context, Bitmap bitmap) {
        if (context == null || bitmap == null) return false;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
            byte[] jpegBytes = baos.toByteArray();
            byte[] encrypted = encrypt(jpegBytes);
            File avatarFile = getVaultFile(context, AVATAR_FILE_NAME);
            writeBytesAtomic(avatarFile, encrypted);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt avatar bitmap", e);
            return false;
        }
    }

    public static synchronized Bitmap loadAvatarBitmap(Context context) {
        if (context == null) return null;
        try {
            File avatarFile = getVaultFile(context, AVATAR_FILE_NAME);
            if (!avatarFile.exists()) {
                return null;
            }
            byte[] encrypted = readAllBytes(avatarFile);
            byte[] jpegBytes = decrypt(encrypted);
            return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt avatar bitmap", e);
            return null;
        }
    }

    public static synchronized boolean hasAvatar(Context context) {
        if (context == null) return false;
        File avatarFile = new File(new File(context.getFilesDir(), VAULT_DIR_NAME), AVATAR_FILE_NAME);
        return avatarFile.exists() && avatarFile.length() > 0;
    }

    public static synchronized void deleteAvatar(Context context) {
        if (context == null) return;
        File avatarFile = new File(new File(context.getFilesDir(), VAULT_DIR_NAME), AVATAR_FILE_NAME);
        if (avatarFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            avatarFile.delete();
        }
    }

    public static synchronized void clearProfile(Context context) {
        if (context == null) return;
        File profileFile = new File(new File(context.getFilesDir(), VAULT_DIR_NAME), PROFILE_FILE_NAME);
        if (profileFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            profileFile.delete();
        }
        deleteAvatar(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_ZEN, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_LEGACY_USER_NAME).apply();
    }

    public static Bitmap decodeAndOrientAvatar(Context context, Uri imageUri) {
        try {
            BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
            boundsOpts.inJustDecodeBounds = true;
            try (InputStream is = context.getContentResolver().openInputStream(imageUri)) {
                if (is == null) return null;
                BitmapFactory.decodeStream(is, null, boundsOpts);
            }

            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return null;
            }

            int sampleSize = 1;
            int maxDim = Math.max(boundsOpts.outWidth, boundsOpts.outHeight);
            while (maxDim / sampleSize > MAX_AVATAR_DIMENSION * 2) {
                sampleSize *= 2;
            }

            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = sampleSize;
            Bitmap decoded;
            try (InputStream is = context.getContentResolver().openInputStream(imageUri)) {
                if (is == null) return null;
                decoded = BitmapFactory.decodeStream(is, null, decodeOpts);
            }
            if (decoded == null) return null;

            int rotationDegrees = 0;
            try (InputStream exifStream = context.getContentResolver().openInputStream(imageUri)) {
                if (exifStream != null) {
                    ExifInterface exif = new ExifInterface(exifStream);
                    int orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                    );
                    switch (orientation) {
                        case ExifInterface.ORIENTATION_ROTATE_90:
                            rotationDegrees = 90;
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_180:
                            rotationDegrees = 180;
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_270:
                            rotationDegrees = 270;
                            break;
                        default:
                            rotationDegrees = 0;
                            break;
                    }
                }
            } catch (Exception ignored) {
            }

            Bitmap rotated = decoded;
            if (rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotationDegrees);
                rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
                if (rotated != decoded) {
                    decoded.recycle();
                }
            }

            // Center-crop square and resize cleanly to MAX_AVATAR_DIMENSION
            int width = rotated.getWidth();
            int height = rotated.getHeight();
            int size = Math.min(width, height);
            int x = (width - size) / 2;
            int y = (height - size) / 2;
            Bitmap squared = Bitmap.createBitmap(rotated, x, y, size, size);
            if (squared != rotated) {
                rotated.recycle();
            }

            if (size > MAX_AVATAR_DIMENSION) {
                Bitmap scaled = Bitmap.createScaledBitmap(squared, MAX_AVATAR_DIMENSION, MAX_AVATAR_DIMENSION, true);
                if (scaled != squared) {
                    squared.recycle();
                }
                return scaled;
            }
            return squared;
        } catch (Exception e) {
            Log.e(TAG, "Failed decoding avatar URI", e);
            return null;
        }
    }

    private static SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }

    private static byte[] encrypt(byte[] plaintext) throws Exception {
        SecretKey key = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeByte(FORMAT_VERSION);
            dos.writeInt(iv.length);
            dos.write(iv);
            dos.writeInt(ciphertext.length);
            dos.write(ciphertext);
        }
        return baos.toByteArray();
    }

    private static byte[] decrypt(byte[] payload) throws Exception {
        SecretKey key = getOrCreateSecretKey();
        try (DataInputStream dis = new DataInputStream(new java.io.ByteArrayInputStream(payload))) {
            byte version = dis.readByte();
            if (version != FORMAT_VERSION) {
                throw new IllegalStateException("Unsupported vault format version: " + version);
            }
            int ivLen = dis.readInt();
            if (ivLen <= 0 || ivLen > 64) {
                throw new IllegalStateException("Invalid IV length");
            }
            byte[] iv = new byte[ivLen];
            dis.readFully(iv);
            int ctLen = dis.readInt();
            if (ctLen <= 0 || ctLen > 16 * 1024 * 1024) {
                throw new IllegalStateException("Invalid ciphertext length");
            }
            byte[] ciphertext = new byte[ctLen];
            dis.readFully(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        }
    }

    private static File getVaultFile(Context context, String fileName) {
        File dir = new File(context.getFilesDir(), VAULT_DIR_NAME);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, fileName);
    }

    private static void writeBytesAtomic(File target, byte[] data) throws Exception {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(data);
            fos.getFD().sync();
        }
        if (target.exists()) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
        }
        if (!tmp.renameTo(target)) {
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(data);
                fos.getFD().sync();
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static byte[] readAllBytes(File file) throws Exception {
        int len = (int) file.length();
        byte[] data = new byte[len];
        try (FileInputStream fis = new FileInputStream(file);
             DataInputStream dis = new DataInputStream(fis)) {
            dis.readFully(data);
        }
        return data;
    }
}
