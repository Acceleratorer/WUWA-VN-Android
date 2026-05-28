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
        append("Source: ")
            .append(result.source)
            .append("\n\n")
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
        val requiredPaths = PatchDryRunPlanner.backupRelativePaths().toSet()
        val legacyMountLangPath = PatchDryRunPlanner.mountLangRelativePath()
        val android332MountLangPath = PatchDryRunPlanner.android332MountLangRelativePath()
        val backedUpPaths = result.backedUpFiles.map { it.relativePath }.toSet()
        val hasAndroid332ResourcesBackup = backedUpPaths.contains(android332MountLangPath)
        val missingLegacyMountLangWithResourcesBackup =
            result.missingFiles.contains(legacyMountLangPath) && hasAndroid332ResourcesBackup
        val missingRequired = result.missingFiles
            .filter { requiredPaths.contains(it) }
            .filterNot { it == legacyMountLangPath && missingLegacyMountLangWithResourcesBackup }
        val missingOptional = result.missingFiles.filterNot { requiredPaths.contains(it) }
        if (missingRequired.isNotEmpty()) {
            append("\nMissing required files:\n")
            for (file in missingRequired) {
                append("- ").append(PatchDryRunPlanner.backupDisplayName(file)).append('\n')
            }
        }
        if (missingLegacyMountLangWithResourcesBackup) {
            append("\nAndroid 3.3.2 Resources backup:\n")
            append("- MountLang_en.Resources-3.3.0.txt: OK\n")
            append("- Legacy MountLang_en.txt path: missing as expected for this layout\n")
        }
        if (missingOptional.isNotEmpty()) {
            append("\nAndroid 3.3.2 Resources files not found:\n")
            for (file in missingOptional) {
                append("- ").append(PatchDryRunPlanner.backupDisplayName(file)).append('\n')
            }
        }
        append("\nBackup folder:\n").append(backupDirectory.absolutePath)
        if (result.isTrustedForWriteActions()) {
            append("\n\nThis backup is trusted for restore, patch install prechecks, config presets, and remove with MountLang rollback.")
        } else if (hasAndroid332ResourcesBackup) {
            append("\n\nThis backup captured the Android 3.3.2 Resources layout for diagnostics and recovery evidence. Write actions remain locked until the Android 3.3.2 install format is confirmed.")
        } else {
            append("\n\nThis backup is read-only but not trusted for write actions yet. Install, presets, remove, and restore stay locked until the required original config set is verified.")
        }
    }
}
