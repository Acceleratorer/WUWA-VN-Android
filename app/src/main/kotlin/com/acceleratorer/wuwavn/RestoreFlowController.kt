package com.acceleratorer.wuwavn

import android.app.Activity
import android.widget.Toast
import java.io.File

class RestoreFlowController(
    private val activity: Activity,
    private val logger: DebugLogger,
    private val restoreDryRunPlanner: RestoreDryRunPlanner,
    private val restoreWriter: ShizukuRestoreWriter,
    private val gamePackageDetector: GamePackageDetector,
    private val shizukuStateChecker: ShizukuStateChecker,
    private val dialogs: DialogFactory,
    private val onRestoreFinished: () -> Unit,
) {
    @Volatile private var restoreDryRunRunning = false
    @Volatile private var restoreWriteRunning = false

    fun showRestoreSessions() {
        val sessions = restoreDryRunPlanner.listBackupSessions(activity)
        if (sessions.isEmpty()) {
            dialogs.showMessage(
                "Restore Original Files",
                "No backup sessions found yet.\n\nRun Backup Game Configs first.",
            )
            logger.add("Restore: no backup sessions found")
            return
        }

        val labels = sessions.map { restoreDryRunPlanner.sessionLabel(it) }.toTypedArray()
        dialogs.showSelection("Select backup", labels) { which ->
            showRestoreDryRun(sessions[which])
        }
        logger.add("Restore: listed ${sessions.size} backup sessions")
    }

    private fun showRestoreDryRun(sessionDirectory: File) {
        if (restoreDryRunRunning) {
            Toast.makeText(activity, "Restore dry run is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        restoreDryRunRunning = true
        logger.add("Restore dry run: started for ${sessionDirectory.name}")

        Thread {
            try {
                val dryRun = restoreDryRunPlanner.plan(sessionDirectory)
                logger.add("Restore dry run: verified ${dryRun.verifiedCount()}/${dryRun.files.size} files")
                activity.runOnUiThread {
                    showRestorePlan(dryRun)
                }
            } catch (exception: Exception) {
                logger.add("Restore dry run failed: ${exception.message}")
                activity.runOnUiThread {
                    dialogs.showMessage("Restore dry run failed", exception.message.orEmpty())
                }
            } finally {
                restoreDryRunRunning = false
            }
        }.start()
    }

    private fun showRestorePlan(dryRun: RestoreDryRun) {
        val gameState = gamePackageDetector.detect(activity)
        val shizukuState = shizukuStateChecker.check(activity)
        val blockReason = restoreBlockReason(dryRun, gameState, shizukuState)
        val summary = restoreDryRunSummary(dryRun, blockReason)

        if (blockReason != null) {
            dialogs.showMessage("Restore dry run", summary)
            logger.add("Restore write: blocked - $blockReason")
            return
        }

        dialogs.showConfirmation(
            title = "Restore dry run",
            message = summary + "\n\nContinue to restore these original files?",
            positiveLabel = "Continue Restore",
        ) {
            showFinalRestoreConfirmation(dryRun)
        }
    }

    private fun showFinalRestoreConfirmation(dryRun: RestoreDryRun) {
        dialogs.showConfirmation(
            title = "Final restore confirmation",
            message = "This will write the verified original config files back to Wuthering Waves Global:\n\n" +
                "- Engine.ini\n" +
                "- DeviceProfiles.ini\n" +
                "- MountLang_en.txt\n\n" +
                "Patch install and config preset writing are separate flows. Continue only if you want to restore the original files.",
            positiveLabel = "Restore Now",
        ) {
            restoreOriginalFiles(dryRun)
        }
    }

    private fun restoreOriginalFiles(dryRun: RestoreDryRun) {
        if (restoreWriteRunning) {
            Toast.makeText(activity, "Restore is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        val gameState = gamePackageDetector.detect(activity)
        val shizukuState = shizukuStateChecker.check(activity)
        val blockReason = restoreBlockReason(dryRun, gameState, shizukuState)
        if (blockReason != null) {
            dialogs.showMessage("Restore blocked", blockReason)
            logger.add("Restore write: blocked - $blockReason")
            return
        }

        restoreWriteRunning = true
        logger.add("Restore write: started")

        Thread {
            try {
                val result = restoreWriter.restoreConfigFiles(activity, dryRun, logger)
                logger.add("Restore write: success")
                activity.runOnUiThread {
                    onRestoreFinished()
                    dialogs.showMessage("Restore complete", restoreWriteSummary(result))
                }
            } catch (exception: Exception) {
                logger.add("Restore write failed: ${exception.message}")
                activity.runOnUiThread {
                    dialogs.showMessage("Restore failed", exception.message.orEmpty())
                }
            } finally {
                restoreWriteRunning = false
            }
        }.start()
    }

    private fun restoreBlockReason(
        dryRun: RestoreDryRun,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ): String? {
        if (!Wuwa36SafetyPolicy.RESTORE_WRITE_ENABLED) {
            return "Restore Original Files write is locked for WUWA 3.6 until the three-file restore flow is transactional. Use Remove Vietnamese Patch for transactional PAK/SIG + MountLang recovery."
        }
        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            return "Wuthering Waves Global is not detected."
        }
        if (shizukuState != ShizukuState.READY) {
            return "Shizuku is not ready yet."
        }
        if (!TrustedBackupPolicy.isTrustedBackup(dryRun)) {
            return "Backup must be a trusted original WUWA 3.6 backup with exactly three verified files."
        }
        return null
    }

    private fun restoreDryRunSummary(
        dryRun: RestoreDryRun,
        blockReason: String?,
    ): String = buildString {
        append("Backup session:\n")
            .append(dryRun.sessionDirectory.absolutePath)
            .append("\n\nCreated at:\n")
            .append(dryRun.createdAt)
            .append("\n\nGame package:\n")
            .append(dryRun.gamePackage.ifEmpty { "unknown" })
            .append("\n\nBackup type:\n")
            .append(dryRun.backupType)
            .append("\n\nrestore_write_enabled:\n")
            .append(dryRun.restoreWriteEnabled?.toString() ?: "missing")
            .append("\n\nFiles checked:\n")

        for (file in dryRun.files) {
            append("- ")
                .append(file.displayName)
                .append(": ")
                .append(file.status.label)
                .append(" (")
                .append(file.sizeBytes)
                .append(" bytes")
            file.actualSha256?.let { hash ->
                append(", SHA-256 ").append(hash.take(12)).append("...")
            }
            append(")\n  target: ")
                .append(file.relativePath.ifEmpty { "unknown" })
                .append('\n')
        }

        append("\nVerified files: ")
            .append(dryRun.verifiedCount())
            .append("/")
            .append(dryRun.files.size)

        if (blockReason == null) {
            append("\n\nRestore write is available for this verified backup.")
        } else {
            append("\n\nRestore writing is blocked:\n").append(blockReason)
        }
        append("\n\nVietnamese PAK install and config preset writing are handled separately.")
    }

    private fun restoreWriteSummary(result: ShizukuRestoreWriter.RestoreResult): String = buildString {
        append("Restored files:\n")
        for (file in result.restoredFiles) {
            append("- ")
                .append(file.displayName)
                .append(" (")
                .append(file.sizeBytes)
                .append(" bytes, SHA-256 ")
                .append(file.sha256.take(12))
                .append("...)\n")
        }
        append("\nAll restored files were re-read from the game folder and verified.")
        append("\n\nPatch install and config preset writing are handled separately.")
    }
}
