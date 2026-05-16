package com.acceleratorer.wuwavn

import android.content.Context

object TrustedBackupPolicy {
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
            dryRun.hasOnlyVerifiedRequiredConfigFiles()
}
