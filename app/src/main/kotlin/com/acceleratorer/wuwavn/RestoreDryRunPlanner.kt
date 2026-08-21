package com.acceleratorer.wuwavn

import android.content.Context
import java.io.File
import org.json.JSONObject

class RestoreDryRunPlanner(
    private val backupManager: BackupManager,
) {
    fun listBackupSessions(context: Context): List<File> =
        backupManager.listBackupSessions(context)

    fun sessionLabel(sessionDirectory: File): String {
        return try {
            val metadata = readMetadata(sessionDirectory)
            val createdAt = metadata.optString("created_at", sessionDirectory.name)
            val files = metadata.optJSONArray("files")
            "$createdAt (${files?.length() ?: 0} files)"
        } catch (exception: Exception) {
            "${sessionDirectory.name} (metadata error)"
        }
    }

    fun plan(sessionDirectory: File): RestoreDryRun {
        val metadata = readMetadata(sessionDirectory)
        val filesJson = metadata.optJSONArray("files")
            ?: throw IllegalStateException("Backup metadata does not contain files.")

        val files = mutableListOf<RestoreFilePlan>()
        for (index in 0 until filesJson.length()) {
            val item = filesJson.optJSONObject(index)
            if (item == null) {
                files.add(
                    RestoreFilePlan(
                        displayName = "metadata item $index",
                        relativePath = "",
                        expectedSha256 = null,
                        actualSha256 = null,
                        sizeBytes = 0L,
                        status = RestoreFileStatus.UNSAFE_METADATA,
                    ),
                )
                continue
            }

            files.add(planFile(sessionDirectory, item))
        }

        val missingFilesJson = metadata.optJSONArray("missing_files")
        if (missingFilesJson != null) {
            for (index in 0 until missingFilesJson.length()) {
                val relativePath = missingFilesJson.optString(index, "")
                files.add(planMissingFile(relativePath))
            }
        }

        if (files.isEmpty()) {
            throw IllegalStateException("Backup metadata has no restorable files.")
        }

        return RestoreDryRun(
            sessionDirectory = sessionDirectory,
            createdAt = metadata.optString("created_at", sessionDirectory.name),
            gamePackage = metadata.optString("game_package", ""),
            backupType = metadata.optString("backup_type", "unknown"),
            restoreWriteEnabled = if (metadata.has("restore_write_enabled")) {
                metadata.optBoolean("restore_write_enabled")
            } else {
                null
            },
            files = files,
        )
    }

    private fun planFile(sessionDirectory: File, item: JSONObject): RestoreFilePlan {
        val relativePath = item.optString("relative_path", "")
        val fallbackName = if (relativePath.isNotEmpty()) {
            PatchDryRunPlanner.displayName(relativePath)
        } else {
            "unknown"
        }
        val displayName = item.optString("display_name", fallbackName)
        val expectedSha256 = item.optString("sha256", "").ifBlank { null }
        val declaredSize = item.optLong("size_bytes", 0L)

        if (!isSafeRestoreMetadata(displayName, relativePath)) {
            return RestoreFilePlan(
                displayName = displayName,
                relativePath = relativePath,
                expectedSha256 = expectedSha256,
                actualSha256 = null,
                sizeBytes = declaredSize,
                status = RestoreFileStatus.UNSAFE_METADATA,
            )
        }

        val backupFile = File(sessionDirectory, displayName)
        if (!backupFile.isFile) {
            return RestoreFilePlan(
                displayName = displayName,
                relativePath = relativePath,
                expectedSha256 = expectedSha256,
                actualSha256 = null,
                sizeBytes = declaredSize,
                status = RestoreFileStatus.MISSING,
            )
        }

        return try {
            val actualSha256 = Sha256Verifier.sha256(backupFile)
            val status = when {
                expectedSha256 == null -> RestoreFileStatus.HASH_NOT_AVAILABLE
                actualSha256.equals(expectedSha256, ignoreCase = true) -> RestoreFileStatus.VERIFIED
                else -> RestoreFileStatus.HASH_MISMATCH
            }
            RestoreFilePlan(
                displayName = displayName,
                relativePath = relativePath,
                expectedSha256 = expectedSha256,
                actualSha256 = actualSha256,
                sizeBytes = backupFile.length(),
                status = status,
            )
        } catch (exception: Exception) {
            RestoreFilePlan(
                displayName = displayName,
                relativePath = relativePath,
                expectedSha256 = expectedSha256,
                actualSha256 = null,
                sizeBytes = declaredSize,
                status = RestoreFileStatus.READ_FAILED,
            )
        }
    }

    private fun planMissingFile(relativePath: String): RestoreFilePlan {
        val displayName = if (relativePath.isNotEmpty()) {
            PatchDryRunPlanner.displayName(relativePath)
        } else {
            "missing metadata item"
        }

        if (!isRequiredBackupPath(relativePath)) {
            return RestoreFilePlan(
                displayName = displayName,
                relativePath = relativePath,
                expectedSha256 = null,
                actualSha256 = null,
                sizeBytes = 0L,
                status = RestoreFileStatus.UNSAFE_METADATA,
            )
        }

        return RestoreFilePlan(
            displayName = displayName,
            relativePath = relativePath,
            expectedSha256 = null,
            actualSha256 = null,
            sizeBytes = 0L,
            status = RestoreFileStatus.NOT_BACKED_UP,
        )
    }

    private fun readMetadata(sessionDirectory: File): JSONObject {
        val metadataFile = File(sessionDirectory, "metadata.json")
        if (!metadataFile.isFile) {
            throw IllegalStateException("metadata.json is missing.")
        }
        return JSONObject(metadataFile.readText(Charsets.UTF_8))
    }

    private fun isSafeRestoreMetadata(displayName: String, relativePath: String): Boolean {
        if (!isRequiredBackupPath(relativePath)) {
            return false
        }
        if (displayName.contains("/") || displayName.contains("\\") || displayName.contains("..")) {
            return false
        }
        return displayName == PatchDryRunPlanner.backupDisplayName(relativePath)
    }

    private fun isRequiredBackupPath(relativePath: String): Boolean =
        PatchDryRunPlanner.backupRelativePaths().contains(relativePath) || WuWa36Layout.isMountLangPath(relativePath)
}
