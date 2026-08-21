package com.acceleratorer.wuwavn

import android.content.Context
import java.io.File

object TrustedBackupPolicy {
    const val REQUIRED_FILE_COUNT = 3

    fun findTrustedBackup(
        context: Context,
        restoreDryRunPlanner: RestoreDryRunPlanner,
    ): TrustedBackupInfo? {
        for (session in restoreDryRunPlanner.listBackupSessions(context)) {
            val dryRun = try {
                restoreDryRunPlanner.plan(session)
            } catch (exception: Exception) {
                null
            } ?: continue

            if (isTrustedBackup(dryRun)) {
                return TrustedBackupInfo(
                    sessionDirectory = dryRun.sessionDirectory,
                    createdAt = dryRun.createdAt,
                    verifiedFiles = dryRun.verifiedCount(),
                )
            }
        }
        return null
    }

    fun isTrustedBackup(dryRun: RestoreDryRun): Boolean =
        dryRun.backupType == BackupManager.READ_ONLY_CONFIG_BACKUP_TYPE &&
            dryRun.gamePackage == AppConstants.GLOBAL_GAME_PACKAGE &&
            dryRun.restoreWriteEnabled == false &&
            dryRun.allFilesVerified() &&
            dryRun.hasOnlyVerifiedRequiredConfigFiles() &&
            hasOriginalMountLang(dryRun)

    private fun hasOriginalMountLang(dryRun: RestoreDryRun): Boolean {
        val mountFiles = dryRun.files.filter {
            it.status == RestoreFileStatus.VERIFIED && WuWa36Layout.isMountLangPath(it.relativePath)
        }
        if (mountFiles.size != 1) return false

        val mountFile = File(dryRun.sessionDirectory, mountFiles.single().displayName)
        return mountFile.isFile &&
            mountFile.length() <= MAX_MOUNT_BYTES &&
            runCatching {
                WuWa36Layout.isOriginalMountLang(mountFile.readText(Charsets.UTF_8))
            }.getOrDefault(false)
    }

    private const val MAX_MOUNT_BYTES = 512 * 1024L
}
