package com.acceleratorer.wuwavn

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private val logger = DebugLogger()
    private val gamePackageDetector = GamePackageDetector()
    private val shizukuStateChecker = ShizukuStateChecker(gamePackageDetector)
    private val backupManager = BackupManager()
    private val dryRunPlanner = PatchDryRunPlanner(backupManager)
    private val manifestRepository = PatchManifestRepository()
    private val shizukuFileSystem = ShizukuFileSystem()
    private val backupReader = ShizukuBackupReader(backupManager)
    private val restoreDryRunPlanner = RestoreDryRunPlanner(backupManager)
    private val downloadClient = DownloadClient()

    private var statusView: TextView? = null
    private var logView: TextView? = null
    @Volatile private var patchPreparationRunning = false
    @Volatile private var backupRunning = false
    @Volatile private var restoreDryRunRunning = false
    private var shizukuState = ShizukuState.NOT_INSTALLED
    private var gameState = GamePackageDetector.State.NOT_INSTALLED
    @Volatile private var lastBackupPath: String? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        logger.add("Shizuku: binder received")
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        logger.add("Shizuku: binder dead")
        refreshStatus()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            logger.add("Shizuku permission result: $grantResult")
            refreshStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 17, 29)
        window.navigationBarColor = Color.rgb(11, 17, 29)

        setContentView(createContentView())
        logger.setListener { text ->
            runOnUiThread {
                logView?.text = text
            }
        }

        registerShizukuListeners()
        logger.add("App version: ${AppConstants.VERSION_NAME} (${AppConstants.VERSION_CODE})")
        logger.add("Android version: ${Build.VERSION.RELEASE}")
        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (ignored: Throwable) {
        }
    }

    private fun createContentView(): View {
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.setBackgroundColor(Color.rgb(11, 17, 29))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(24), dp(20), dp(24))
        scroll.addView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val title = text("WUWA VN Android", 26, Color.WHITE, true)
        root.addView(title)

        val subtitle = text("Safe patch manager for Vietnamese Wuthering Waves players.", 14, Color.rgb(182, 193, 211), false)
        subtitle.setPadding(0, dp(8), 0, dp(16))
        root.addView(subtitle)

        statusView = text("", 15, Color.WHITE, false).also {
            it.setPadding(dp(14), dp(14), dp(14), dp(14))
            it.setBackgroundColor(Color.rgb(22, 32, 49))
            root.addView(it, matchWrap())
        }

        root.addView(space(14))
        root.addView(primaryButton("Show Patch Plan") { showPatchDryRun() })
        root.addView(button("Backup Game Configs") { backupGameConfigs() })
        root.addView(button("Copy Backup Path") { copyBackupPath() })
        root.addView(button("Download & Verify Patch") { preparePatchSafely() })
        root.addView(button("Update Vietnamese Patch") {
            openUrl(AppConstants.RELEASES_URL)
            logger.add("Update check: opened GitHub Releases")
        })
        root.addView(button("Restore Original Files") {
            showRestoreDryRunSessions()
        })
        root.addView(button("Check Game Folder") {
            refreshStatus()
            logger.add("Game folder: checked package state")
        })
        root.addView(button("Open Shizuku") { openOrRequestShizuku() })

        root.addView(space(18))
        root.addView(text("Safety rules", 18, Color.WHITE, true))
        val safety = text(
            "Only allowlisted WUWA targets can be planned:\n" +
                "- Engine.ini\n" +
                "- DeviceProfiles.ini\n" +
                "- MountLang_en.txt\n" +
                "- WuWaVH_99_P.pak\n\n" +
                "Always backup first. Never use this app for cheating, anti-cheat bypass, or gameplay manipulation.",
            14,
            Color.rgb(198, 207, 220),
            false,
        )
        safety.setPadding(0, dp(8), 0, dp(12))
        root.addView(safety)

        root.addView(text("Debug log", 18, Color.WHITE, true))
        logView = text("", 12, Color.rgb(209, 218, 230), false).also {
            it.typeface = Typeface.MONOSPACE
            it.setPadding(dp(12), dp(12), dp(12), dp(12))
            it.setBackgroundColor(Color.rgb(7, 12, 21))
            root.addView(it, matchWrap())
        }

        root.addView(button("Copy Debug Log") { copyLog() })
        root.addView(button("Send Issue Report") { shareLog() })

        return scroll
    }

    private fun registerShizukuListeners() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (throwable: Throwable) {
            logger.add("Shizuku listeners: unavailable")
        }
    }

    private fun refreshStatus() {
        gameState = gamePackageDetector.detect(this)
        shizukuState = shizukuStateChecker.check(this)
        val manifest = manifestRepository.current()

        statusView?.text =
            "Status\n" +
                "Game: ${gameState.label}\n" +
                "Shizuku: ${shizukuState.label}\n" +
                "Patch: ${manifest.patchVersion}\n" +
                "Patch SHA-256: ${manifest.pakSha256.take(12)}...\n" +
                "Mode: Safe / Default\n" +
                "File writing: ${if (shizukuFileSystem.isWriteEnabled(shizukuState)) "enabled" else "locked"}"
    }

    private fun showPatchDryRun() {
        logger.add("Dry run: started")
        try {
            val dryRun = dryRunPlanner.plan(this)
            lastBackupPath = dryRun.backupDirectory.absolutePath
            var message = dryRun.describe() + "\n\n" + shizukuFileSystem.disabledReason(shizukuState)
            if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
                message = "Global Wuthering Waves package is not detected.\n\n$message"
            }
            AlertDialog.Builder(this)
                .setTitle("Dry run")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
            logger.add("Dry run: allowlist verified")
            logger.add("Backup target planned: ${dryRun.backupDirectory.absolutePath}")
        } catch (exception: RuntimeException) {
            showMessage("Dry run failed", exception.message.orEmpty())
            logger.add("Dry run: failed - ${exception.message}")
        }
    }

    private fun preparePatchSafely() {
        if (patchPreparationRunning) {
            Toast.makeText(this, "Patch preparation is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        refreshStatus()
        patchPreparationRunning = true
        logger.add("Patch preparation: started")

        Thread {
            val manifest = manifestRepository.current()
            try {
                dryRunPlanner.plan(this)
                logger.add("Dry run: allowlist verified before download")

                val patchFile = downloadClient.downloadAndVerify(
                    this,
                    manifest,
                    DownloadClient.ProgressListener { message -> logger.add(message) },
                )

                logger.add("Patch file: ${patchFile.absolutePath}")
                runOnUiThread {
                    showMessage(
                        "Patch verified",
                        "Patch was downloaded and verified successfully.\n\nUse Backup Game Configs to test read-only Shizuku backup. Game file writing is still locked.",
                    )
                }
            } catch (exception: Exception) {
                logger.add("Patch preparation failed: ${exception.message}")
                runOnUiThread {
                    showMessage("Patch preparation failed", exception.message.orEmpty())
                }
            } finally {
                patchPreparationRunning = false
            }
        }.start()
    }

    private fun backupGameConfigs() {
        if (backupRunning) {
            Toast.makeText(this, "Backup is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        refreshStatus()
        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            showMessage("Backup blocked", "Wuthering Waves Global is not detected. Install the Global version before backing up game config files.")
            logger.add("Read-only backup: blocked - WUWA Global not detected")
            return
        }
        if (shizukuState != ShizukuState.READY) {
            showMessage("Backup blocked", shizukuFileSystem.disabledReason(shizukuState))
            logger.add("Read-only backup: blocked - Shizuku not ready")
            return
        }

        backupRunning = true
        logger.add("Read-only backup: started")
        val detectedGameState = gameState

        Thread {
            val manifest = manifestRepository.current()
            try {
                val backupDirectory = backupManager.createBackupDirectory(this)
                lastBackupPath = backupDirectory.absolutePath
                logger.add("Backup path: ${backupDirectory.absolutePath}")

                val result = backupReader.backupConfigFiles(this, backupDirectory, logger)
                backupManager.writeBackupMetadata(backupDirectory, manifest, detectedGameState, result.backedUpFiles, result.missingFiles)
                logger.add("Backup metadata: wrote actual backed-up files")
                logger.add("Read-only backup: success")

                runOnUiThread {
                    showMessage("Backup complete", backupSummary(backupDirectory, result))
                }
            } catch (exception: Exception) {
                logger.add("Read-only backup failed: ${exception.message}")
                runOnUiThread {
                    showMessage("Backup failed", exception.message.orEmpty())
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
                append("- ").append(file).append('\n')
            }
        }
        append("\nBackup folder:\n").append(backupDirectory.absolutePath)
        append("\n\nThis version only reads game files. Patch writing and restore writing remain locked.")
    }

    private fun showRestoreDryRunSessions() {
        val sessions = restoreDryRunPlanner.listBackupSessions(this)
        if (sessions.isEmpty()) {
            showMessage(
                "Restore dry run",
                "No backup sessions found yet.\n\nRun Backup Game Configs first. Restore writing remains locked.",
            )
            logger.add("Restore dry run: no backup sessions found")
            return
        }

        val labels = sessions.map { restoreDryRunPlanner.sessionLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select backup")
            .setItems(labels) { _, which ->
                showRestoreDryRun(sessions[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
        logger.add("Restore dry run: listed ${sessions.size} backup sessions")
    }

    private fun showRestoreDryRun(sessionDirectory: File) {
        if (restoreDryRunRunning) {
            Toast.makeText(this, "Restore dry run is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        restoreDryRunRunning = true
        logger.add("Restore dry run: started for ${sessionDirectory.name}")

        Thread {
            try {
                val dryRun = restoreDryRunPlanner.plan(sessionDirectory)
                logger.add("Restore dry run: verified ${dryRun.verifiedCount()}/${dryRun.files.size} files")
                runOnUiThread {
                    showMessage("Restore dry run", restoreDryRunSummary(dryRun))
                }
            } catch (exception: Exception) {
                logger.add("Restore dry run failed: ${exception.message}")
                runOnUiThread {
                    showMessage("Restore dry run failed", exception.message.orEmpty())
                }
            } finally {
                restoreDryRunRunning = false
            }
        }.start()
    }

    private fun restoreDryRunSummary(dryRun: RestoreDryRun): String = buildString {
        append("Backup session:\n")
            .append(dryRun.sessionDirectory.absolutePath)
            .append("\n\nCreated at:\n")
            .append(dryRun.createdAt)
            .append("\n\nBackup type:\n")
            .append(dryRun.backupType)
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
        append("\n\nRestore writing is still locked in v2.4.0. No game files were modified.")
    }

    private fun openOrRequestShizuku() {
        if (shizukuState == ShizukuState.RUNNING_PERMISSION_DENIED && shizukuStateChecker.requestPermissionIfPossible(this)) {
            logger.add("Shizuku: permission request sent")
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(AppConstants.SHIZUKU_PACKAGE)
        if (launchIntent != null) {
            startActivity(launchIntent)
            logger.add("Shizuku: opened app")
            return
        }
        openUrl("https://shizuku.rikka.app/")
        logger.add("Shizuku: opened install guide")
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (ignored: ActivityNotFoundException) {
            Toast.makeText(this, "No browser app found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLog() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("WUWA VN debug log", logger.text()))
            Toast.makeText(this, "Debug log copied.", Toast.LENGTH_SHORT).show()
            logger.add("Log: copied")
        }
    }

    private fun copyBackupPath() {
        val backupPath = lastBackupPath
        if (backupPath.isNullOrEmpty()) {
            showMessage("Backup path", "No backup path yet. Run Show Patch Plan or Backup Game Configs first.")
            logger.add("Backup path: not available yet")
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("WUWA VN backup path", backupPath))
            Toast.makeText(this, "Backup path copied.", Toast.LENGTH_SHORT).show()
            logger.add("Backup path: copied")
        }
    }

    private fun shareLog() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, "WUWA VN issue report")
        intent.putExtra(Intent.EXTRA_TEXT, logger.text())
        startActivity(Intent.createChooser(intent, "Send Issue Report"))
        logger.add("Issue report: share sheet opened")
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun text(value: String, sp: Int, color: Int, bold: Boolean): TextView {
        val textView = TextView(this)
        textView.text = value
        textView.textSize = sp.toFloat()
        textView.setTextColor(color)
        textView.setLineSpacing(dp(2).toFloat(), 1.0f)
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }
        return textView
    }

    private fun primaryButton(label: String, listener: View.OnClickListener): Button {
        val button = button(label, listener)
        button.setTextColor(Color.WHITE)
        button.setBackgroundColor(Color.rgb(25, 118, 210))
        return button
    }

    private fun button(label: String, listener: View.OnClickListener): Button {
        val button = Button(this)
        button.text = label
        button.setTextColor(Color.WHITE)
        button.isAllCaps = false
        button.gravity = Gravity.CENTER
        button.setBackgroundColor(Color.rgb(35, 48, 68))
        button.setOnClickListener(listener)
        val params = matchWrap()
        params.setMargins(0, dp(8), 0, 0)
        button.layoutParams = params
        return button
    }

    private fun space(heightDp: Int): View {
        val view = View(this)
        view.layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
        return view
    }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

    private fun dp(value: Int): Int =
        Math.round(value * resources.displayMetrics.density)
}
