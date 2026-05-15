package com.acceleratorer.wuwavn;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
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
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
        return new File(root, timestamp);
    }

    File createBackupSession(Context context, PatchManifest manifest, PatchDryRun dryRun, GamePackageDetector.State gameState) {
        File session = planBackupSession(context);
        if (!session.exists() && !session.mkdirs()) {
            throw new IllegalStateException("Could not create backup directory: " + session.getAbsolutePath());
        }
        writeMetadata(session, manifest, dryRun, gameState);
        return session;
    }

    private void writeMetadata(File session, PatchManifest manifest, PatchDryRun dryRun, GamePackageDetector.State gameState) {
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
            metadata.put("backup_type", "metadata_only_until_shizuku_file_copy_is_tested");

            JSONArray files = new JSONArray();
            for (String file : dryRun.metadataFiles) {
                files.put(file);
            }
            metadata.put("files", files);

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
