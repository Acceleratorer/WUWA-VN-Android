package com.acceleratorer.wuwavn

import android.app.Activity
import android.widget.Toast
import java.io.File

class BackupFlowController(
    private val activity: Activity,
    private val logger: DebugLogger,
    private val backupManager: BackupManager,
    private val manifestRepository: PatchManifestRepository,
    private val shizukuFileSystem: ShizukuFileSystem,
    private val backupReader: ShizukuBackupReader,
    private val dialogs: DialogFactory,
    private val onBackupPath: (String) -> Unit,
) {
    @Volatile private var backupRunning = false

    fun backupGameConfigs(
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ) {
        if (backupRunning) {
            Toast.makeText(activity, "Backup is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            dialogs.showMessage("Backup blocked", "Wuthering Waves Global is not detected. Install the Global version before backing up game config files.")
            logger.add("Read-only backup: blocked - WUWA Global not detected")
            return
        }
        if (shizukuState != ShizukuState.READY) {
            dialogs.showMessage("Backup blocked", shizukuFileSystem.disabledReason(shizukuState))
            logger.add("Read-only backup: blocked - Shizuku not ready")
            return
        }

        backupRunning = true
        logger.add("Read-only backup: started")
        val detectedGameState = gameState

        Thread {
            val manifest = manifestRepository.current()
            try {
                val backupDirectory = backupManager.createBackupDirectory(activity)
                onBackupPath(backupDirectory.absolutePath)
                logger.add("Backup path: ${backupDirectory.absolutePath}")

                val result = backupReader.backupConfigFiles(activity, backupDirectory, logger)
                backupManager.writeBackupMetadata(backupDirectory, manifest, detectedGameState, result.backedUpFiles, result.missingFiles)
                logger.add("Backup metadata: wrote actual backed-up files")
                logger.add("Read-only backup: success")

                activity.runOnUiThread {
                    dialogs.showMessage("Backup complete", backupSummary(backupDirectory, result))
                }
            } catch (exception: Exception) {
                logger.add("Read-only backup failed: ${exception.message}")
                activity.runOnUiThread {
                    dialogs.showMessage("Backup failed", exception.message.orEmpty())
                }
            } finally {
                backupRunning = false
            }
        }.start()
    }

    private fun backupSummary(
        backupDirectory: File,
        result: ShizukuBackupReader.BackupResult,
    ): String = buildString {
        append("Backed up files:\n")
        for (file in result.backedUpFiles) {
            append("- ")
                .append(file.displayName)
                .append(" (")
                .append(file.sizeBytes)
                .append(" bytes, SHA-256 ")
                .append(file.sha256.take(12))
                .append("...)\n")
        }
        if (result.missingFiles.isNotEmpty()) {
            append("\nMissing files:\n")
            for (file in result.missingFiles) {
                append("- ").append(PatchDryRunPlanner.displayName(file)).append('\n')
            }
        }
        append("\nBackup folder:\n").append(backupDirectory.absolutePath)
        append("\n\nThis version can restore verified original config files, install the verified PAK, apply Safe / Default config preset, and remove the PAK with MountLang rollback.")
    }
}
