package com.example.util;

import com.example.model.ClassSchedule;
import com.example.model.ZenBackupPayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class providing robust Gson serialization, deserialization,
 * and I/O helpers for ZEN backup, restore, and cohort timetable sharing.
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
     * Deserializes a JSON string back into a ZenBackupPayload instance.
     */
    public static ZenBackupPayload deserialize(String json) throws JsonSyntaxException {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty or null JSON payload");
        }
        return GSON.fromJson(json, ZenBackupPayload.class);
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
