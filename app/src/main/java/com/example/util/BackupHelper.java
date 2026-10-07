package com.example.util;

import com.example.model.AttendanceHistory;
import com.example.model.ClassSchedule;
import com.example.model.Note;
import com.example.model.Task;
import com.example.model.ZenBackupPayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class providing robust Gson serialization, deserialization,
 * smart legacy migration, and I/O helpers for ZEN backup, restore, and cohort timetable sharing.
 */
public class BackupHelper {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private BackupHelper() {
        // Utility class
    }

    /**
     * Serializes a ZenBackupPayload into a formatted JSON string.
     */
    public static String serialize(ZenBackupPayload payload) {
        if (payload == null) {
            return "{}";
        }
        return GSON.toJson(payload);
    }

    /**
     * "Smart Import" Deserializer & Legacy Migration:
     * Parses the backup JSON safely handling both modern decoupled backups and legacy backups.
     *
     * In legacy backups:
     * attendance_records contains scheduleId, subjectName, status, date, but lacks creditHours and classType.
     * The Smart Lookup:
     * - Checks if creditHours and classType are present.
     * - If missing, matches legacy scheduleId to the schedules array in the same backup file.
     * - Extracts creditHours and classType from that matched schedule object.
     * - Reconstructs the new AttendanceHistory snapshot entity.
     * - Respects History-Only backup mode (schedules, notes, and tasks remain empty/null).
     */
    public static ZenBackupPayload deserialize(String json) throws JsonSyntaxException {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty or null JSON payload");
        }

        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) {
            throw new JsonSyntaxException("Invalid backup JSON: root element must be a JSON object");
        }

        JsonObject rootObj = rootElement.getAsJsonObject();
        ZenBackupPayload payload = new ZenBackupPayload();

        if (rootObj.has("app_name") && !rootObj.get("app_name").isJsonNull()) {
            payload.setAppName(rootObj.get("app_name").getAsString());
        }
        if (rootObj.has("version") && !rootObj.get("version").isJsonNull()) {
            payload.setVersion(rootObj.get("version").getAsString());
        }
        if (rootObj.has("backup_type") && !rootObj.get("backup_type").isJsonNull()) {
            payload.setBackupType(rootObj.get("backup_type").getAsString());
        }
        if (rootObj.has("export_timestamp") && !rootObj.get("export_timestamp").isJsonNull()) {
            payload.setExportTimestamp(rootObj.get("export_timestamp").getAsLong());
        }

        // 1. Parse Notes (if present and non-empty)
        List<Note> notesList = new ArrayList<>();
        if (rootObj.has("notes") && !rootObj.get("notes").isJsonNull() && rootObj.get("notes").isJsonArray()) {
            JsonArray notesArr = rootObj.getAsJsonArray("notes");
            for (JsonElement elem : notesArr) {
                if (elem != null && elem.isJsonObject()) {
                    try {
                        Note note = GSON.fromJson(elem, Note.class);
                        if (note != null) notesList.add(note);
                    } catch (Exception ignored) {}
                }
            }
        }
        payload.setNotes(notesList);

        // 2. Parse Tasks (if present and non-empty)
        List<Task> tasksList = new ArrayList<>();
        if (rootObj.has("tasks") && !rootObj.get("tasks").isJsonNull() && rootObj.get("tasks").isJsonArray()) {
            JsonArray tasksArr = rootObj.getAsJsonArray("tasks");
            for (JsonElement elem : tasksArr) {
                if (elem != null && elem.isJsonObject()) {
                    try {
                        Task task = GSON.fromJson(elem, Task.class);
                        if (task != null) tasksList.add(task);
                    } catch (Exception ignored) {}
                }
            }
        }
        payload.setTasks(tasksList);

        // 3. Parse Schedules and index by schedule ID and subject name for Smart Lookup
        List<ClassSchedule> schedulesList = new ArrayList<>();
        Map<Integer, ClassSchedule> scheduleIdMap = new HashMap<>();
        Map<String, ClassSchedule> scheduleSubjectMap = new HashMap<>();

        if (rootObj.has("schedules") && !rootObj.get("schedules").isJsonNull() && rootObj.get("schedules").isJsonArray()) {
            JsonArray schedulesArr = rootObj.getAsJsonArray("schedules");
            for (JsonElement elem : schedulesArr) {
                if (elem != null && elem.isJsonObject()) {
                    try {
                        ClassSchedule cs = GSON.fromJson(elem, ClassSchedule.class);
                        if (cs != null) {
                            schedulesList.add(cs);
                            if (cs.getId() > 0) {
                                scheduleIdMap.put(cs.getId(), cs);
                            }
                            if (cs.getSubjectName() != null) {
                                scheduleSubjectMap.put(cs.getSubjectName().trim().toLowerCase(Locale.ROOT), cs);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        payload.setSchedules(schedulesList);

        // 4. Parse Attendance Records with Smart Migration
        List<AttendanceHistory> historyList = new ArrayList<>();
        JsonArray recordsArr = null;
        if (rootObj.has("attendance_records") && !rootObj.get("attendance_records").isJsonNull() && rootObj.get("attendance_records").isJsonArray()) {
            recordsArr = rootObj.getAsJsonArray("attendance_records");
        } else if (rootObj.has("attendance_history") && !rootObj.get("attendance_history").isJsonNull() && rootObj.get("attendance_history").isJsonArray()) {
            recordsArr = rootObj.getAsJsonArray("attendance_history");
        }

        if (recordsArr != null) {
            for (JsonElement elem : recordsArr) {
                if (elem == null || !elem.isJsonObject()) continue;
                JsonObject recObj = elem.getAsJsonObject();

                // Extract Subject Name (supports subject_name or subjectName)
                String subjectName = "";
                if (recObj.has("subject_name") && !recObj.get("subject_name").isJsonNull()) {
                    subjectName = recObj.get("subject_name").getAsString();
                } else if (recObj.has("subjectName") && !recObj.get("subjectName").isJsonNull()) {
                    subjectName = recObj.get("subjectName").getAsString();
                }

                // Extract Date
                long date = 0L;
                if (recObj.has("date") && !recObj.get("date").isJsonNull()) {
                    try {
                        date = recObj.get("date").getAsLong();
                    } catch (Exception ignored) {}
                }

                // Extract Status (default: Present)
                String status = AttendanceHistory.STATUS_PRESENT;
                if (recObj.has("status") && !recObj.get("status").isJsonNull()) {
                    status = recObj.get("status").getAsString();
                }

                // Check for creditHours (supports credit_hours or creditHours)
                int creditHours = 0;
                if (recObj.has("credit_hours") && !recObj.get("credit_hours").isJsonNull()) {
                    try {
                        creditHours = recObj.get("credit_hours").getAsInt();
                    } catch (Exception ignored) {}
                } else if (recObj.has("creditHours") && !recObj.get("creditHours").isJsonNull()) {
                    try {
                        creditHours = recObj.get("creditHours").getAsInt();
                    } catch (Exception ignored) {}
                }

                // Check for classType (supports class_type or classType)
                String classType = null;
                if (recObj.has("class_type") && !recObj.get("class_type").isJsonNull()) {
                    classType = recObj.get("class_type").getAsString();
                } else if (recObj.has("classType") && !recObj.get("classType").isJsonNull()) {
                    classType = recObj.get("classType").getAsString();
                }

                // Check legacy scheduleId
                int legacyScheduleId = -1;
                if (recObj.has("schedule_id") && !recObj.get("schedule_id").isJsonNull()) {
                    try {
                        legacyScheduleId = recObj.get("schedule_id").getAsInt();
                    } catch (Exception ignored) {}
                } else if (recObj.has("scheduleId") && !recObj.get("scheduleId").isJsonNull()) {
                    try {
                        legacyScheduleId = recObj.get("scheduleId").getAsInt();
                    } catch (Exception ignored) {}
                }

                // Smart Lookup for legacy records lacking creditHours or classType
                if (creditHours <= 0 || classType == null || classType.trim().isEmpty()) {
                    ClassSchedule matchedSchedule = null;
                    if (legacyScheduleId > 0) {
                        matchedSchedule = scheduleIdMap.get(legacyScheduleId);
                    }
                    if (matchedSchedule == null && !subjectName.trim().isEmpty()) {
                        matchedSchedule = scheduleSubjectMap.get(subjectName.trim().toLowerCase(Locale.ROOT));
                    }

                    if (matchedSchedule != null) {
                        if (creditHours <= 0) {
                            creditHours = matchedSchedule.getCreditHours() > 0 ? matchedSchedule.getCreditHours() : 1;
                        }
                        if (classType == null || classType.trim().isEmpty()) {
                            classType = matchedSchedule.getClassType();
                        }
                        if (subjectName.trim().isEmpty() && matchedSchedule.getSubjectName() != null) {
                            subjectName = matchedSchedule.getSubjectName();
                        }
                    }
                }

                // Final fallbacks for robustness
                if (creditHours <= 0) creditHours = 1;
                if (classType == null || classType.trim().isEmpty()) classType = "Theory";
                if (subjectName.trim().isEmpty()) subjectName = "Attendance";

                AttendanceHistory history = new AttendanceHistory(
                        subjectName,
                        creditHours,
                        classType,
                        date,
                        status
                );
                historyList.add(history);
            }
        }
        payload.setAttendanceRecords(historyList);

        return payload;
    }

    /**
     * Serializes only ClassSchedules into a cohort timetable sharing payload.
     */
    public static String serializeSchedulesForCohort(List<ClassSchedule> schedules) {
        ZenBackupPayload payload = new ZenBackupPayload(
                ZenBackupPayload.TYPE_TIMETABLE_ONLY,
                null,
                null,
                schedules,
                null
        );
        return serialize(payload);
    }

    /**
     * Generates a descriptive, timestamped filename for a backup file.
     */
    public static String generateBackupFileName(String backupType) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String typeSlug = backupType.toLowerCase(Locale.ROOT).replace(" ", "_");
        return "zen_backup_" + typeSlug + "_" + timestamp + ".json";
    }

    /**
     * Writes a JSON string to an OutputStream using UTF-8 encoding.
     */
    public static void writeStringToStream(OutputStream outputStream, String content) throws IOException {
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writer.write(content);
            writer.flush();
        }
    }

    /**
     * Reads a full string from an InputStream using UTF-8 encoding.
     */
    public static String readStringFromStream(InputStream inputStream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        }
        return stringBuilder.toString();
    }
}
