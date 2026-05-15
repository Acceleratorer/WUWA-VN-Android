package com.acceleratorer.wuwavn

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

class BackupManager {
    fun planBackupSession(context: Context): File {
        val root = context.getExternalFilesDir("WUWA-VH-Backup")
            ?: File(context.filesDir, "WUWA-VH-Backup")
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        return File(root, timestamp)
    }

    fun createBackupDirectory(context: Context): File {
        val session = planBackupSession(context)
        if (!session.exists() && !session.mkdirs()) {
            throw IllegalStateException("Could not create backup directory: ${session.absolutePath}")
        }
        return session
    }

    fun writeBackedUpFile(
        session: File,
        displayName: String,
        relativePath: String,
        content: ByteArray,
    ): BackupFileInfo {
        try {
            if (!PatchDryRunPlanner.backupRelativePaths().contains(relativePath)) {
                throw SecurityException("Blocked non-backup target: $relativePath")
            }
            if (displayName.contains("/") || displayName.contains("\\") || displayName.contains("..")) {
                throw SecurityException("Blocked unsafe backup file name: $displayName")
            }

            if (!session.exists() && !session.mkdirs()) {
                throw IllegalStateException("Could not create backup directory: ${session.absolutePath}")
            }

            val destination = File(session, displayName)
            FileOutputStream(destination).use { output ->
                output.write(content)
            }

            if (!destination.exists()) {
                throw IllegalStateException("Backup file was not written: $displayName")
            }

            return BackupFileInfo(
                displayName = displayName,
                relativePath = relativePath,
                sha256 = Sha256Verifier.sha256(destination),
                sizeBytes = destination.length(),
            )
        } catch (exception: Exception) {
            throw IllegalStateException("Could not write backup file: ${exception.message}", exception)
        }
    }

    fun writeBackupMetadata(
        session: File,
        manifest: PatchManifest,
        gameState: GamePackageDetector.State,
        backedUpFiles: List<BackupFileInfo>,
        missingFiles: List<String>,
    ) {
        try {
            val metadata = JSONObject()
                .put("created_at", isoTimestamp())
                .put("game_package", AppConstants.GLOBAL_GAME_PACKAGE)
                .put("game_state", gameState.name)
                .put("app_version", AppConstants.VERSION_NAME)
                .put("app_version_code", AppConstants.VERSION_CODE)
                .put("patch_version", manifest.patchVersion)
                .put("patch_url", manifest.pakUrl)
                .put("patch_sha256", manifest.pakSha256)
                .put("backup_type", "shizuku_read_only_config_backup")
                .put("game_write_enabled", false)
                .put("restore_write_enabled", false)

            val files = JSONArray()
            for (file in backedUpFiles) {
                files.put(
                    JSONObject()
                        .put("display_name", file.displayName)
                        .put("relative_path", file.relativePath)
                        .put("sha256", file.sha256)
                        .put("size_bytes", file.sizeBytes),
                )
            }
            metadata.put("files", files)

            val missing = JSONArray()
            for (missingFile in missingFiles) {
                missing.put(missingFile)
            }
            metadata.put("missing_files", missing)

            val metadataFile = File(session, "metadata.json")
            FileOutputStream(metadataFile).use { output ->
                output.write(metadata.toString(2).toByteArray(Charsets.UTF_8))
            }

            if (!metadataFile.exists() || metadataFile.length() == 0L) {
                throw IllegalStateException("Backup metadata was not written.")
            }
        } catch (exception: Exception) {
            throw IllegalStateException("Could not write backup metadata: ${exception.message}", exception)
        }
    }

    private fun isoTimestamp(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        format.timeZone = TimeZone.getDefault()
        return format.format(Date())
    }
}
