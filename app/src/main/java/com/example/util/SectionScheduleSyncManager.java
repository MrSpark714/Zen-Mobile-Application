package com.example.util;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.database.AppDatabase;
import com.example.database.ClassScheduleDao;
import com.example.model.ActivityLog;
import com.example.model.ClassSchedule;
import com.example.model.ScheduleSyncModels.ScheduleIndexManifest;
import com.example.model.ScheduleSyncModels.ScheduleUpdatePreview;
import com.example.model.ScheduleSyncModels.SectionIndexEntry;
import com.example.model.ScheduleSyncModels.SectionSchedulePackage;
import com.example.model.ScheduleSyncModels.SyncedClassEntry;
import com.example.model.UserProfile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coordinates battery-conscious, privacy-preserving GitHub section schedule checks,
 * local cohort matching, cryptographic verification, and smart diff merges.
 */
public final class SectionScheduleSyncManager {

    private static final int MAX_DOWNLOAD_BYTES = 512 * 1024; // 512 KB safety limit
    private static final int TIMEOUT_MS = 5000;

    private static final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface HttpTransport {
        HttpResponse fetch(String url, String ifNoneMatchETag) throws IOException;
    }

    public static class HttpResponse {
        public final int statusCode;
        public final String body;
        public final String eTag;

        public HttpResponse(int statusCode, String body, String eTag) {
            this.statusCode = statusCode;
            this.body = body;
            this.eTag = eTag;
        }
    }

    public enum SyncOutcomeType {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        NOT_MODIFIED_304,
        COOLDOWN_ACTIVE,
        PROFILE_INCOMPLETE,
        SECTION_NOT_FOUND,
        KEY_NOT_CONFIGURED,
        VERIFICATION_FAILED,
        NETWORK_ERROR
    }

    public static class SyncCheckResult {
        public final SyncOutcomeType outcome;
        public final String message;
        public final ScheduleUpdatePreview preview;

        public SyncCheckResult(SyncOutcomeType outcome, String message, ScheduleUpdatePreview preview) {
            this.outcome = outcome;
            this.message = message;
            this.preview = preview;
        }
    }

    public interface SyncCallback {
        void onResult(SyncCheckResult result);
    }

    public interface MergeCallback {
        void onComplete(boolean success, String summaryMessage);
    }

    private static volatile HttpTransport customTransportForTesting = null;

    private SectionScheduleSyncManager() {
    }

    public static void setCustomTransportForTesting(HttpTransport transport) {
        customTransportForTesting = transport;
    }

    /**
     * Checks whether the local student profile has the four cohort matching fields populated.
     */
    public static boolean hasMatchableCohortProfile(UserProfile profile) {
        if (profile == null) return false;
        return !profile.getUniversityName().trim().isEmpty()
                && !profile.getDepartment().trim().isEmpty()
                && !profile.getCurrentSemester().trim().isEmpty()
                && !profile.getCampusSection().trim().isEmpty();
    }

    /**
     * Runs an asynchronous check against the public GitHub index manifest.
     * Automatic checks honor the auto-sync toggle, 6-hour cooldown, ETag 304, and skipped versions.
     * Manual checks bypass cooldown and skipped-version suppression.
     */
    public static void checkForScheduleUpdates(
            @NonNull Context context,
            boolean forceManualCheck,
            @NonNull SyncCallback callback) {
        final Context appContext = context.getApplicationContext();
        syncExecutor.execute(() -> {
            SyncCheckResult result = checkForScheduleUpdatesSync(
                    appContext,
                    forceManualCheck,
                    customTransportForTesting != null ? customTransportForTesting : SectionScheduleSyncManager::defaultHttpsFetch
            );
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * Synchronous core check logic (safe for background threads and deterministic Robolectric testing).
     */
    public static SyncCheckResult checkForScheduleUpdatesSync(
            @NonNull Context context,
            boolean forceManualCheck,
            @NonNull HttpTransport transport) {
        UserProfile profile = SecureProfileStore.loadProfile(context);
        return checkForScheduleUpdatesWithProfileSync(context, profile, forceManualCheck, transport);
    }

    public static SyncCheckResult checkForScheduleUpdatesWithProfileSync(
            @NonNull Context context,
            UserProfile profile,
            boolean forceManualCheck,
            @NonNull HttpTransport transport) {

        if (!forceManualCheck && !ScheduleSyncPreferences.isAutoSyncEnabled(context)) {
            return new SyncCheckResult(SyncOutcomeType.COOLDOWN_ACTIVE, "Auto-sync disabled", null);
        }

        if (!hasMatchableCohortProfile(profile)) {
            return new SyncCheckResult(
                    SyncOutcomeType.PROFILE_INCOMPLETE,
                    "Add University, Department, Semester, and Section in Profile to sync schedules.",
                    null
            );
        }

        String publicKey = ScheduleSyncPreferences.getEffectivePublicKey(context);
        if (!ScheduleCryptoVerifier.isValidPublicKeySpki(publicKey)) {
            return new SyncCheckResult(
                    SyncOutcomeType.KEY_NOT_CONFIGURED,
                    "Schedule signing public key is not configured yet in Settings.",
                    null
            );
        }

        long now = System.currentTimeMillis();
        if (!forceManualCheck && !ScheduleSyncPreferences.isCooldownElapsed(context, now)) {
            return new SyncCheckResult(SyncOutcomeType.COOLDOWN_ACTIVE, "Checked recently", null);
        }

        String baseUrl = ScheduleSyncPreferences.getRepoBaseUrl(context);
        String indexUrl = baseUrl + "index.json";
        String savedETag = forceManualCheck ? "" : ScheduleSyncPreferences.getIndexETag(context);

        HttpResponse indexResponse;
        try {
            indexResponse = transport.fetch(indexUrl, savedETag);
        } catch (Exception e) {
            return new SyncCheckResult(
                    SyncOutcomeType.NETWORK_ERROR,
                    "Could not reach schedule repository: " + e.getMessage(),
                    null
            );
        }

        ScheduleSyncPreferences.setLastCheckMillis(context, now);

        if (indexResponse.statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return new SyncCheckResult(SyncOutcomeType.NOT_MODIFIED_304, "Schedule index unchanged", null);
        }

        if (indexResponse.statusCode < 200 || indexResponse.statusCode >= 300 || indexResponse.body == null) {
            return new SyncCheckResult(
                    SyncOutcomeType.NETWORK_ERROR,
                    "Schedule repository returned HTTP " + indexResponse.statusCode,
                    null
            );
        }

        ScheduleIndexManifest manifest;
        try {
            manifest = ScheduleCryptoVerifier.verifyAndParseIndex(indexResponse.body, publicKey);
        } catch (SecurityException se) {
            return new SyncCheckResult(
                    SyncOutcomeType.VERIFICATION_FAILED,
                    se.getMessage(),
                    null
            );
        }

        if (indexResponse.eTag != null && !indexResponse.eTag.trim().isEmpty()) {
            ScheduleSyncPreferences.setIndexETag(context, indexResponse.eTag.trim());
        }

        SectionIndexEntry matched = findMatchingSection(profile, manifest);
        if (matched == null) {
            return new SyncCheckResult(
                    SyncOutcomeType.SECTION_NOT_FOUND,
                    "No matching section schedule found in repository for your profile.",
                    null
            );
        }

        String appliedSectionId = ScheduleSyncPreferences.getMatchedSectionId(context);
        ScheduleSyncPreferences.setMatchedSection(context, matched.sectionId, matched.getDisplayLabel());

        String appliedVersion = ScheduleSyncPreferences.getAppliedVersion(context);
        String skippedVersion = ScheduleSyncPreferences.getSkippedVersion(context);

        if (!forceManualCheck && !skippedVersion.isEmpty()
                && compareVersions(matched.latestVersion, skippedVersion) <= 0) {
            return new SyncCheckResult(
                    SyncOutcomeType.UP_TO_DATE,
                    "Version " + matched.latestVersion + " was skipped",
                    null
            );
        }

        // Download encrypted section package
        String packageUrl = baseUrl + matched.packagePath;
        HttpResponse packageResponse;
        try {
            packageResponse = transport.fetch(packageUrl, "");
        } catch (Exception e) {
            return new SyncCheckResult(
                    SyncOutcomeType.NETWORK_ERROR,
                    "Failed to download section package: " + e.getMessage(),
                    null
            );
        }

        if (packageResponse.statusCode < 200 || packageResponse.statusCode >= 300 || packageResponse.body == null) {
            return new SyncCheckResult(
                    SyncOutcomeType.NETWORK_ERROR,
                    "Section package returned HTTP " + packageResponse.statusCode,
                    null
            );
        }

        ScheduleCryptoVerifier.VerifiedPackageResult verified;
        try {
            String passphrase = ScheduleSyncPreferences.getOptionalPassphrase(context);
            verified = ScheduleCryptoVerifier.verifyAndDecryptPackage(
                    packageResponse.body,
                    matched.sectionId,
                    matched.packageSha256,
                    publicKey,
                    passphrase
            );
        } catch (SecurityException se) {
            return new SyncCheckResult(
                    SyncOutcomeType.VERIFICATION_FAILED,
                    se.getMessage(),
                    null
            );
        }

        AppDatabase db = AppDatabase.getInstance(context);
        List<ClassSchedule> localAll = db.classScheduleDao().getAllSchedulesSync();
        String currentVersionLabel = appliedVersion.isEmpty() ? "Manual / None" : appliedVersion;

        ScheduleUpdatePreview preview = computeScheduleDiff(
                matched,
                verified.schedulePackage,
                verified.plaintextSha256,
                currentVersionLabel,
                localAll
        );

        boolean sameSectionAndVersion = matched.sectionId.equalsIgnoreCase(appliedSectionId)
                && !appliedVersion.isEmpty()
                && compareVersions(verified.schedulePackage.version, appliedVersion) <= 0;

        if (!preview.hasChanges || (sameSectionAndVersion && !forceManualCheck)) {
            if (!preview.hasChanges && appliedVersion.isEmpty()) {
                ScheduleSyncPreferences.setAppliedVersion(context, verified.schedulePackage.version);
            }
            return new SyncCheckResult(
                    SyncOutcomeType.UP_TO_DATE,
                    "Your schedule is already up to date (" + verified.schedulePackage.version + ").",
                    null
            );
        }

        return new SyncCheckResult(
                SyncOutcomeType.UPDATE_AVAILABLE,
                "Update " + verified.schedulePackage.version + " ready",
                preview
        );
    }

    /**
     * Performs 100% on-device cohort matching against normalized institute, department, semester, and section tokens.
     */
    public static SectionIndexEntry findMatchingSection(UserProfile profile, ScheduleIndexManifest manifest) {
        if (profile == null || manifest == null) return null;
        String userUni = normalizeGenericToken(profile.getUniversityName());
        String userDept = normalizeDepartmentToken(profile.getDepartment());
        String userSem = normalizeSemesterToken(profile.getCurrentSemester());
        String userSec = normalizeSectionToken(profile.getCampusSection());

        if (userUni.isEmpty() || userDept.isEmpty() || userSem.isEmpty() || userSec.isEmpty()) {
            return null;
        }

        for (SectionIndexEntry entry : manifest.getSections()) {
            if (entry == null) continue;
            if (matchesTokenOrAliases(userUni, entry.institute, entry.instituteAliases, TokenKind.GENERIC)
                    && matchesTokenOrAliases(userDept, entry.department, entry.departmentAliases, TokenKind.DEPARTMENT)
                    && matchesTokenOrAliases(userSem, entry.semester, entry.semesterAliases, TokenKind.SEMESTER)
                    && matchesTokenOrAliases(userSec, entry.section, entry.sectionAliases, TokenKind.SECTION)) {
                return entry;
            }
        }
        return null;
    }

    private enum TokenKind {
        GENERIC, DEPARTMENT, SEMESTER, SECTION
    }

    private static boolean matchesTokenOrAliases(
            String normalizedUserValue,
            String primaryValue,
            List<String> aliases,
            TokenKind kind) {
        if (normalizeByKind(primaryValue, kind).equals(normalizedUserValue)) {
            return true;
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (normalizeByKind(alias, kind).equals(normalizedUserValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizeByKind(String raw, TokenKind kind) {
        switch (kind) {
            case DEPARTMENT:
                return normalizeDepartmentToken(raw);
            case SEMESTER:
                return normalizeSemesterToken(raw);
            case SECTION:
                return normalizeSectionToken(raw);
            default:
                return normalizeGenericToken(raw);
        }
    }

    static String normalizeGenericToken(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    static String normalizeDepartmentToken(String raw) {
        String s = normalizeGenericToken(raw)
                .replaceAll("\\b(department|dept|of|faculty|school)\\b", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (s.equals("computer science") || s.equals("comp sci") || s.equals("bscs")) return "cs";
        if (s.equals("software engineering") || s.equals("bsse")) return "se";
        if (s.equals("cyber security") || s.equals("cybersecurity")) return "cys";
        if (s.equals("artificial intelligence")) return "ai";
        if (s.equals("information technology")) return "it";
        if (s.equals("data science")) return "ds";
        return s;
    }

    static String normalizeSemesterToken(String raw) {
        String s = normalizeGenericToken(raw)
                .replaceAll("\\b(semester|sem|term)\\b", " ")
                .trim()
                .replaceAll("\\b(\\d+)(st|nd|rd|th)\\b", "$1")
                .replaceAll("\\s+", " ");
        if (s.matches("^0+\\d+$")) {
            s = s.replaceFirst("^0+", "");
        }
        return s;
    }

    static String normalizeSectionToken(String raw) {
        return normalizeGenericToken(raw)
                .replaceAll("\\b(section|sec|batch|group)\\b", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /**
     * Semantic version comparator handling labels like V01, V1.0, V1.1, V1.10, V2.
     */
    public static int compareVersions(String v1, String v2) {
        List<Integer> p1 = parseVersionParts(v1);
        List<Integer> p2 = parseVersionParts(v2);
        int max = Math.max(p1.size(), p2.size());
        for (int i = 0; i < max; i++) {
            int a = i < p1.size() ? p1.get(i) : 0;
            int b = i < p2.size() ? p2.get(i) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    private static List<Integer> parseVersionParts(String version) {
        List<Integer> parts = new ArrayList<>();
        if (version == null) return parts;
        String cleaned = version.trim().replaceFirst("^[Vv]+", "");
        String[] segments = cleaned.split("[^0-9]+");
        for (String seg : segments) {
            if (!seg.isEmpty()) {
                try {
                    parts.add(Integer.parseInt(seg));
                } catch (NumberFormatException ignored) {
                    parts.add(0);
                }
            }
        }
        return parts;
    }

    /**
     * Computes a non-destructive smart diff between the existing local timetable and a verified remote package.
     * Personal (MANUAL) classes and AttendanceHistory records are never altered or removed.
     */
    public static ScheduleUpdatePreview computeScheduleDiff(
            SectionIndexEntry indexEntry,
            SectionSchedulePackage pkg,
            String plaintextSha256,
            String fromVersion,
            List<ClassSchedule> localAll) {

        Map<String, ClassSchedule> existingSyncedByKey = new HashMap<>();
        List<ClassSchedule> manualSlots = new ArrayList<>();

        if (localAll != null) {
            for (ClassSchedule item : localAll) {
                if (item == null) continue;
                if (item.isSynced() && !item.getSyncSlotKey().isEmpty()) {
                    existingSyncedByKey.put(item.getSyncSlotKey(), item);
                } else {
                    manualSlots.add(item);
                }
            }
        }

        List<ClassSchedule> toInsert = new ArrayList<>();
        List<ClassSchedule> toUpdate = new ArrayList<>();
        List<ClassSchedule> toDelete = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        Set<String> remoteKeysSeen = new HashSet<>();
        int unchangedCount = 0;

        for (SyncedClassEntry remote : pkg.getClasses()) {
            remoteKeysSeen.add(remote.slotKey);

            ClassSchedule candidate = new ClassSchedule(
                    remote.dayOfWeek,
                    remote.subjectName,
                    remote.startTime,
                    remote.endTime,
                    remote.creditHours,
                    remote.classType,
                    remote.roomNumber
            );
            candidate.setSource(ClassSchedule.SOURCE_SYNCED);
            candidate.setSyncSlotKey(remote.slotKey);
            candidate.setSyncedVersion(pkg.version);

            ClassSchedule existingSynced = existingSyncedByKey.get(remote.slotKey);
            if (existingSynced != null) {
                if (!existingSynced.hasSameScheduleContent(candidate)) {
                    candidate.setId(existingSynced.getId());
                    toUpdate.add(candidate);
                    summaryLines.add("~ " + candidate.getSubjectName() + " (" + shortDay(candidate.getDayOfWeek())
                            + " " + candidate.getStartTime() + ")");
                } else {
                    unchangedCount++;
                }
            } else {
                // Avoid duplicating if the student already entered this exact slot manually
                boolean matchesManualSlot = false;
                for (ClassSchedule manual : manualSlots) {
                    if (manual.hasSameScheduleContent(candidate)) {
                        matchesManualSlot = true;
                        break;
                    }
                }
                if (matchesManualSlot) {
                    unchangedCount++;
                } else {
                    toInsert.add(candidate);
                    summaryLines.add("+ " + candidate.getSubjectName() + " (" + shortDay(candidate.getDayOfWeek())
                            + " " + candidate.getStartTime() + ")");
                }
            }
        }

        for (Map.Entry<String, ClassSchedule> entry : existingSyncedByKey.entrySet()) {
            if (!remoteKeysSeen.contains(entry.getKey())) {
                ClassSchedule removed = entry.getValue();
                toDelete.add(removed);
                summaryLines.add("- " + removed.getSubjectName() + " (" + shortDay(removed.getDayOfWeek())
                        + " " + removed.getStartTime() + ")");
            }
        }

        return new ScheduleUpdatePreview(
                indexEntry,
                pkg,
                fromVersion,
                pkg.version,
                plaintextSha256,
                toInsert,
                toUpdate,
                toDelete,
                summaryLines,
                unchangedCount
        );
    }

    public static void applyScheduleUpdateAsync(
            @NonNull Context context,
            @NonNull ScheduleUpdatePreview preview,
            @NonNull MergeCallback callback) {
        final Context appContext = context.getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String summary = applyScheduleUpdateSync(appContext, preview);
                mainHandler.post(() -> callback.onComplete(true, summary));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(false, "Failed to apply schedule update: " + e.getMessage()));
            }
        });
    }

    /**
     * Atomically applies the smart diff merge inside a single Room transaction.
     * AttendanceHistory and MANUAL ClassSchedule rows are untouched.
     */
    public static String applyScheduleUpdateSync(
            @NonNull Context context,
            @NonNull ScheduleUpdatePreview preview) {
        AppDatabase db = AppDatabase.getInstance(context);
        ClassScheduleDao dao = db.classScheduleDao();

        db.runInTransaction(() -> {
            if (!preview.slotsToDelete.isEmpty()) {
                for (ClassSchedule del : preview.slotsToDelete) {
                    dao.deleteById(del.getId());
                }
            }
            if (!preview.slotsToUpdate.isEmpty()) {
                for (ClassSchedule upd : preview.slotsToUpdate) {
                    dao.update(upd);
                }
            }
            if (!preview.slotsToInsert.isEmpty()) {
                dao.insertAll(preview.slotsToInsert);
            }
            String logDetail = String.format(
                    Locale.getDefault(),
                    "Merged %s (%s): +%d added, %d updated, %d removed",
                    preview.indexEntry.getDisplayLabel(),
                    preview.toVersion,
                    preview.slotsToInsert.size(),
                    preview.slotsToUpdate.size(),
                    preview.slotsToDelete.size()
            );
            db.activityLogDao().insert(new ActivityLog(
                    System.currentTimeMillis(),
                    "Schedule Sync: " + logDetail
            ));
        });

        ScheduleSyncPreferences.setMatchedSection(
                context,
                preview.verifiedPackage.sectionId,
                preview.indexEntry.getDisplayLabel()
        );
        ScheduleSyncPreferences.setAppliedVersion(context, preview.toVersion);
        ScheduleSyncPreferences.setSkippedVersion(context, "");

        String summary = String.format(
                Locale.getDefault(),
                "Updated to %s (+%d added, %d updated, %d removed)",
                preview.toVersion,
                preview.slotsToInsert.size(),
                preview.slotsToUpdate.size(),
                preview.slotsToDelete.size()
        );
        ScheduleSyncPreferences.setLastSyncSummary(context, summary);
        return summary;
    }

    private static HttpResponse defaultHttpsFetch(String urlString, String ifNoneMatchETag) throws IOException {
        Uri uri = Uri.parse(urlString);
        if (uri == null || uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Blocked non-HTTPS schedule URL");
        }
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
        if (host.isEmpty() || ScheduleSyncPreferences.normalizeSafeHttpsBaseUrl(urlString) == null) {
            throw new IOException("Blocked untrusted schedule host");
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            if (ifNoneMatchETag != null && !ifNoneMatchETag.trim().isEmpty()) {
                conn.setRequestProperty("If-None-Match", ifNoneMatchETag.trim());
            }

            int status = conn.getResponseCode();
            String eTag = conn.getHeaderField("ETag");
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return new HttpResponse(status, null, eTag);
            }
            if (status < 200 || status >= 300) {
                return new HttpResponse(status, null, eTag);
            }

            try (InputStream is = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = is.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("Remote schedule package exceeded 512 KB size limit");
                    }
                    out.write(buffer, 0, read);
                }
                return new HttpResponse(status, new String(out.toByteArray(), StandardCharsets.UTF_8), eTag);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String shortDay(String day) {
        if (day == null || day.length() < 3) return day != null ? day : "";
        return day.substring(0, 3);
    }
}
