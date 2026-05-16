package com.acceleratorer.wuwavn

import android.app.Activity
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
    private val restoreWriter = ShizukuRestoreWriter()
    private val downloadClient = DownloadClient()
    private val configPresetPreconditionChecker = ConfigPresetPreconditionChecker(restoreDryRunPlanner)
    private val configPresetWriter = ShizukuConfigPresetWriter()
    private val patchWritePreconditionChecker = PatchWritePreconditionChecker(
        manifestRepository,
        downloadClient,
        restoreDryRunPlanner,
    )
    private val patchWriter = ShizukuPatchWriter()
    private val statusRenderer = StatusRenderer(manifestRepository, shizukuFileSystem)

    private lateinit var dialogs: DialogFactory
    private lateinit var patchPreparationController: PatchPreparationController
    private lateinit var backupFlowController: BackupFlowController
    private lateinit var restoreFlowController: RestoreFlowController
    private lateinit var configPresetController: ConfigPresetController

    private var statusView: TextView? = null
    private var logView: TextView? = null
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

        initializeControllers()
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

    private fun initializeControllers() {
        dialogs = DialogFactory(this)
        patchPreparationController = PatchPreparationController(
            activity = this,
            logger = logger,
            dryRunPlanner = dryRunPlanner,
            manifestRepository = manifestRepository,
            shizukuFileSystem = shizukuFileSystem,
            downloadClient = downloadClient,
            patchWritePreconditionChecker = patchWritePreconditionChecker,
            patchWriter = patchWriter,
            gamePackageDetector = gamePackageDetector,
            shizukuStateChecker = shizukuStateChecker,
            dialogs = dialogs,
            onBackupPath = { path -> lastBackupPath = path },
        )
        backupFlowController = BackupFlowController(
            activity = this,
            logger = logger,
            backupManager = backupManager,
            manifestRepository = manifestRepository,
            shizukuFileSystem = shizukuFileSystem,
            backupReader = backupReader,
            dialogs = dialogs,
            onBackupPath = { path -> lastBackupPath = path },
        )
        restoreFlowController = RestoreFlowController(
            activity = this,
            logger = logger,
            restoreDryRunPlanner = restoreDryRunPlanner,
            restoreWriter = restoreWriter,
            gamePackageDetector = gamePackageDetector,
            shizukuStateChecker = shizukuStateChecker,
            dialogs = dialogs,
            onRestoreFinished = { refreshStatus() },
        )
        configPresetController = ConfigPresetController(
            activity = this,
            logger = logger,
            preconditionChecker = configPresetPreconditionChecker,
            configPresetWriter = configPresetWriter,
            gamePackageDetector = gamePackageDetector,
            shizukuStateChecker = shizukuStateChecker,
            dialogs = dialogs,
            onPresetFinished = { refreshStatus() },
        )
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

        root.addView(heroImage())

        statusView = text("", 15, Color.WHITE, false).also {
            it.setPadding(dp(14), dp(14), dp(14), dp(14))
            it.setBackgroundColor(Color.rgb(22, 32, 49))
            root.addView(it, matchWrap())
        }

        root.addView(space(14))
        root.addView(primaryButton("Install Vietnamese Patch") {
            refreshStatus()
            patchPreparationController.showPatchWriteDryRun(gameState, shizukuState)
        })
        root.addView(button("Show Patch Plan") {
            refreshStatus()
            patchPreparationController.showPatchDryRun(gameState, shizukuState)
        })
        root.addView(button("Backup Game Configs") {
            refreshStatus()
            backupFlowController.backupGameConfigs(gameState, shizukuState)
        })
        root.addView(button("Copy Backup Path") { copyBackupPath() })
        root.addView(button("Download & Verify Patch") { patchPreparationController.preparePatchSafely() })
        root.addView(button("Apply Safe Config Preset") {
            refreshStatus()
            configPresetController.showSafeDefaultDryRun(gameState, shizukuState)
        })
        root.addView(button("Update Vietnamese Patch") {
            openUrl(AppConstants.RELEASES_URL)
            logger.add("Update check: opened GitHub Releases")
        })
        root.addView(button("Restore Original Files") {
            restoreFlowController.showRestoreSessions()
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

    private fun heroImage(): ImageView {
        val image = ImageView(this)
        image.setImageResource(R.drawable.phrolova_header)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.contentDescription = "WUWA VN header artwork"
        val params = matchWrap()
        params.height = dp(132)
        params.setMargins(0, 0, 0, dp(16))
        image.layoutParams = params
        return image
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
        statusView?.text = statusRenderer.render(gameState, shizukuState)
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
        dialogs.showMessage(title, message)
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
