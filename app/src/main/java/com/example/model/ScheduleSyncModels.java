package com.example.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data models for the tamper-resistant GitHub section schedule synchronization system:
 * - ScheduleIndexManifest: Signed central index (index.json) matching cohort profiles locally.
 * - EncryptedScheduleEnvelope: Signed + SHA-256 verified + AES-256-GCM encrypted section container.
 * - SectionSchedulePackage: Decrypted validated timetable payload for a university section.
 * - ScheduleUpdatePreview: Diff breakdown computed against local ClassSchedule entries.
 */
public final class ScheduleSyncModels {

    private ScheduleSyncModels() {
    }

    public static class ScheduleIndexManifest {
        @SerializedName("schema_version")
        public int schemaVersion = 1;

        @SerializedName("repo_name")
        public String repoName = "";

        @SerializedName("generated_at")
        public String generatedAt = "";

        @SerializedName("sections")
        public List<SectionIndexEntry> sections = new ArrayList<>();

        @SerializedName("signature")
        public String signature = "";

        public List<SectionIndexEntry> getSections() {
            return sections != null ? sections : Collections.emptyList();
        }
    }

    public static class SectionIndexEntry {
        @SerializedName("section_id")
        public String sectionId = "";

        @SerializedName("institute")
        public String institute = "";

        @SerializedName("institute_aliases")
        public List<String> instituteAliases = new ArrayList<>();

        @SerializedName("department")
        public String department = "";

        @SerializedName("department_aliases")
        public List<String> departmentAliases = new ArrayList<>();

        @SerializedName("semester")
        public String semester = "";

        @SerializedName("semester_aliases")
        public List<String> semesterAliases = new ArrayList<>();

        @SerializedName("section")
        public String section = "";

        @SerializedName("section_aliases")
        public List<String> sectionAliases = new ArrayList<>();

        @SerializedName("latest_version")
        public String latestVersion = "V1.0";

        @SerializedName("package_path")
        public String packagePath = "";

        @SerializedName("package_sha256")
        public String packageSha256 = "";

        public String getDisplayLabel() {
            StringBuilder sb = new StringBuilder();
            if (department != null && !department.trim().isEmpty()) {
                sb.append(department.trim());
            }
            if (semester != null && !semester.trim().isEmpty()) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append("Sem ").append(semester.trim());
            }
            if (section != null && !section.trim().isEmpty()) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append("Sec ").append(section.trim());
            }
            return sb.length() > 0 ? sb.toString() : sectionId;
        }
    }

    public static class EncryptedScheduleEnvelope {
        public static final String FORMAT_STANDARD = "ZEN-SCHED-ENC-1";
        public static final String FORMAT_PASSPHRASE = "ZEN-SCHED-ENC-PASSPHRASE-1";

        @SerializedName("format")
        public String format = FORMAT_STANDARD;

        @SerializedName("section_id")
        public String sectionId = "";

        @SerializedName("version")
        public String version = "";

        @SerializedName("published_at")
        public String publishedAt = "";

        @SerializedName("changelog")
        public String changelog = "";

        @SerializedName("iv")
        public String iv = "";

        @SerializedName("salt")
        public String salt = "";

        @SerializedName("payload")
        public String payload = "";

        @SerializedName("plaintext_sha256")
        public String plaintextSha256 = "";

        @SerializedName("signature")
        public String signature = "";
    }

    public static class SectionSchedulePackage {
        @SerializedName("section_id")
        public String sectionId = "";

        @SerializedName("institute")
        public String institute = "";

        @SerializedName("department")
        public String department = "";

        @SerializedName("semester")
        public String semester = "";

        @SerializedName("section")
        public String section = "";

        @SerializedName("version")
        public String version = "V1.0";

        @SerializedName("changelog")
        public String changelog = "";

        @SerializedName("classes")
        public List<SyncedClassEntry> classes = new ArrayList<>();

        public List<SyncedClassEntry> getClasses() {
            return classes != null ? classes : Collections.emptyList();
        }
    }

    public static class SyncedClassEntry {
        @SerializedName("slot_key")
        public String slotKey = "";

        @SerializedName("day_of_week")
        public String dayOfWeek = "";

        @SerializedName("subject_name")
        public String subjectName = "";

        @SerializedName("start_time")
        public String startTime = "";

        @SerializedName("end_time")
        public String endTime = "";

        @SerializedName("credit_hours")
        public int creditHours = 3;

        @SerializedName("class_type")
        public String classType = "Theory";

        @SerializedName("room_number")
        public String roomNumber = "";
    }

    public static class ScheduleUpdatePreview {
        public final SectionIndexEntry indexEntry;
        public final SectionSchedulePackage verifiedPackage;
        public final String fromVersion;
        public final String toVersion;
        public final String plaintextSha256;
        public final List<ClassSchedule> slotsToInsert;
        public final List<ClassSchedule> slotsToUpdate;
        public final List<ClassSchedule> slotsToDelete;
        public final List<String> diffSummaryLines;
        public final int unchangedCount;
        public final boolean hasChanges;

        public ScheduleUpdatePreview(
                SectionIndexEntry indexEntry,
                SectionSchedulePackage verifiedPackage,
                String fromVersion,
                String toVersion,
                String plaintextSha256,
                List<ClassSchedule> slotsToInsert,
                List<ClassSchedule> slotsToUpdate,
                List<ClassSchedule> slotsToDelete,
                List<String> diffSummaryLines,
                int unchangedCount) {
            this.indexEntry = indexEntry;
            this.verifiedPackage = verifiedPackage;
            this.fromVersion = fromVersion != null ? fromVersion : "";
            this.toVersion = toVersion != null ? toVersion : "";
            this.plaintextSha256 = plaintextSha256 != null ? plaintextSha256 : "";
            this.slotsToInsert = slotsToInsert != null ? slotsToInsert : Collections.emptyList();
            this.slotsToUpdate = slotsToUpdate != null ? slotsToUpdate : Collections.emptyList();
            this.slotsToDelete = slotsToDelete != null ? slotsToDelete : Collections.emptyList();
            this.diffSummaryLines = diffSummaryLines != null ? diffSummaryLines : Collections.emptyList();
            this.unchangedCount = unchangedCount;
            this.hasChanges = !this.slotsToInsert.isEmpty()
                    || !this.slotsToUpdate.isEmpty()
                    || !this.slotsToDelete.isEmpty();
        }
    }
}
