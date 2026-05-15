package com.acceleratorer.wuwavn;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;

final class BackupManager {
    File planBackupSession(Context context) {
        File root = context.getExternalFilesDir("WUWA-VH-Backup");
        if (root == null) {
            root = new File(context.getFilesDir(), "WUWA-VH-Backup");
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        return new File(root, timestamp);
    }

    File createBackupDirectory(Context context) {
        File session = planBackupSession(context);
        if (!session.exists() && !session.mkdirs()) {
            throw new IllegalStateException("Could not create backup directory: " + session.getAbsolutePath());
        }
        return session;
    }

    BackupFileInfo writeBackedUpFile(File session, String displayName, String relativePath, byte[] content) {
        try {
            if (!PatchDryRunPlanner.backupRelativePaths().contains(relativePath)) {
                throw new SecurityException("Blocked non-backup target: " + relativePath);
            }
            if (displayName.contains("/") || displayName.contains("\\") || displayName.contains("..")) {
                throw new SecurityException("Blocked unsafe backup file name: " + displayName);
            }

            if (!session.exists() && !session.mkdirs()) {
                throw new IllegalStateException("Could not create backup directory: " + session.getAbsolutePath());
            }

            File destination = new File(session, displayName);
            try (FileOutputStream output = new FileOutputStream(destination)) {
                output.write(content);
            }

            if (!destination.exists()) {
                throw new IllegalStateException("Backup file was not written: " + displayName);
            }

            return new BackupFileInfo(
                    displayName,
                    relativePath,
                    Sha256Verifier.sha256(destination),
                    destination.length()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not write backup file: " + exception.getMessage(), exception);
        }
    }

    void writeBackupMetadata(
            File session,
            PatchManifest manifest,
            GamePackageDetector.State gameState,
            List<BackupFileInfo> backedUpFiles,
            List<String> missingFiles
    ) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("created_at", isoTimestamp());
            metadata.put("game_package", AppConstants.GLOBAL_GAME_PACKAGE);
            metadata.put("game_state", gameState.name());
            metadata.put("app_version", AppConstants.VERSION_NAME);
            metadata.put("app_version_code", AppConstants.VERSION_CODE);
            metadata.put("patch_version", manifest.patchVersion);
            metadata.put("patch_url", manifest.pakUrl);
            metadata.put("patch_sha256", manifest.pakSha256);
            metadata.put("backup_type", "shizuku_read_only_config_backup");
            metadata.put("game_write_enabled", false);
            metadata.put("restore_write_enabled", false);

            JSONArray files = new JSONArray();
            for (BackupFileInfo file : backedUpFiles) {
                JSONObject item = new JSONObject();
                item.put("display_name", file.displayName);
                item.put("relative_path", file.relativePath);
                item.put("sha256", file.sha256);
                item.put("size_bytes", file.sizeBytes);
                files.put(item);
            }
            metadata.put("files", files);

            JSONArray missing = new JSONArray();
            for (String missingFile : missingFiles) {
                missing.put(missingFile);
            }
            metadata.put("missing_files", missing);

            File metadataFile = new File(session, "metadata.json");
            try (FileOutputStream output = new FileOutputStream(metadataFile)) {
                output.write(metadata.toString(2).getBytes(StandardCharsets.UTF_8));
            }

            if (!metadataFile.exists() || metadataFile.length() == 0L) {
                throw new IllegalStateException("Backup metadata was not written.");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not write backup metadata: " + exception.getMessage(), exception);
        }
    }

    private String isoTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date());
    }
}
