package com.acceleratorer.wuwavn

import android.app.Activity
import android.widget.Toast

class PatchPreparationController(
    private val activity: Activity,
    private val logger: DebugLogger,
    private val dryRunPlanner: PatchDryRunPlanner,
    private val manifestRepository: PatchManifestRepository,
    private val shizukuFileSystem: ShizukuFileSystem,
    private val downloadClient: DownloadClient,
    private val dialogs: DialogFactory,
    private val onBackupPath: (String) -> Unit,
) {
    @Volatile private var patchPreparationRunning = false

    fun showPatchDryRun(
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ) {
        logger.add("Dry run: started")
        try {
            val dryRun = dryRunPlanner.plan(activity)
            onBackupPath(dryRun.backupDirectory.absolutePath)
            var message = dryRun.describe() + "\n\n" + shizukuFileSystem.disabledReason(shizukuState)
            if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
                message = "Global Wuthering Waves package is not detected.\n\n$message"
            }
            dialogs.showMessage("Dry run", message)
            logger.add("Dry run: allowlist verified")
            logger.add("Backup target planned: ${dryRun.backupDirectory.absolutePath}")
        } catch (exception: RuntimeException) {
            dialogs.showMessage("Dry run failed", exception.message.orEmpty())
            logger.add("Dry run: failed - ${exception.message}")
        }
    }

    fun preparePatchSafely() {
        if (patchPreparationRunning) {
            Toast.makeText(activity, "Patch preparation is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        patchPreparationRunning = true
        logger.add("Patch preparation: started")

        Thread {
            val manifest = manifestRepository.current()
            try {
                dryRunPlanner.plan(activity)
                logger.add("Dry run: allowlist verified before download")

                val patchFile = downloadClient.downloadAndVerify(
                    activity,
                    manifest,
                    DownloadClient.ProgressListener { message -> logger.add(message) },
                )

                logger.add("Patch file: ${patchFile.absolutePath}")
                activity.runOnUiThread {
                    dialogs.showMessage(
                        "Patch verified",
                        "Patch was downloaded and verified successfully.\n\nUse Backup Game Configs before restore or future patch apply. Patch writing is still locked.",
                    )
                }
            } catch (exception: Exception) {
                logger.add("Patch preparation failed: ${exception.message}")
                activity.runOnUiThread {
                    dialogs.showMessage("Patch preparation failed", exception.message.orEmpty())
                }
            } finally {
                patchPreparationRunning = false
            }
        }.start()
    }
}
