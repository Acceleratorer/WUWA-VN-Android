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
    private val trustedBackupFinder = TrustedBackupFinder(restoreDryRunPlanner)
    private val balancedPresetDryRunPlanner = BalancedPresetDryRunPlanner(
        trustedBackupFinder,
        ConfigPresetDiffPlanner(),
    )
    private val installedStateDetector = InstalledStateDetector(
        ShizukuInstalledStateReader(),
        trustedBackupFinder,
        ConfigStateDetector(),
    )
    private val configPresetPreconditionChecker = ConfigPresetPreconditionChecker(restoreDryRunPlanner)
    private val configPresetWriter = ShizukuConfigPresetWriter()
    private val patchWritePreconditionChecker = PatchWritePreconditionChecker(
        manifestRepository,
        downloadClient,
        restoreDryRunPlanner,
    )
    private val removePatchPreconditionChecker = RemovePatchPreconditionChecker(trustedBackupFinder)
    private val patchWriter = ShizukuPatchWriter()
    private val statusRenderer = StatusRenderer(manifestRepository, shizukuFileSystem)

    private lateinit var dialogs: DialogFactory
    private lateinit var patchPreparationController: PatchPreparationController
    private lateinit var backupFlowController: BackupFlowController
    private lateinit var restoreFlowController: RestoreFlowController
    private lateinit var configPresetController: ConfigPresetController

    private var statusView: TextView? = null
    private var logView: TextView? = null
    private lateinit var installPatchButton: Button
    private lateinit var backupButton: Button
    private lateinit var downloadPatchButton: Button
    private lateinit var applySafeButton: Button
    private lateinit var applyBalancedButton: Button
    private lateinit var removePatchButton: Button
    private lateinit var restoreButton: Button
    private var shizukuState = ShizukuState.NOT_INSTALLED
    private var gameState = GamePackageDetector.State.NOT_INSTALLED
    private var gameInfo: GamePackageDetector.GameInfo? = null
    private var installedState: InstalledState? = null
    private var actionState: HomeActionState? = null
    private var lastStateSignature: String? = null
    private var lastAction: String = "App started"
    private var pendingConfigPresetName: String? = null
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
            removePatchPreconditionChecker = removePatchPreconditionChecker,
            patchWriter = patchWriter,
            restoreWriter = restoreWriter,
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
            balancedPresetDryRunPlanner = balancedPresetDryRunPlanner,
            configPresetWriter = configPresetWriter,
            gamePackageDetector = gamePackageDetector,
            shizukuStateChecker = shizukuStateChecker,
            installedStateProvider = { detectInstalledStateSnapshot() },
            dialogs = dialogs,
            onPresetFinished = {
                lastAction = "${pendingConfigPresetName ?: "Config"} preset applied"
                pendingConfigPresetName = null
                refreshStatus()
            },
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
        installPatchButton = primaryButton("Install Vietnamese Patch") {
            refreshStatus()
            if (blockIfDisabled(installPatchButton, "Install Vietnamese Patch")) return@primaryButton
            lastAction = "Install Vietnamese Patch requested"
            patchPreparationController.showPatchWriteDryRun(gameState, shizukuState)
        }
        root.addView(installPatchButton)
        root.addView(button("Show Patch Plan") {
            refreshStatus()
            lastAction = "Show Patch Plan"
            patchPreparationController.showPatchDryRun(gameState, shizukuState)
        })
        backupButton = button("Backup Game Configs") {
            refreshStatus()
            if (blockIfDisabled(backupButton, "Backup Game Configs")) return@button
            lastAction = "Backup Game Configs requested"
            backupFlowController.backupGameConfigs(gameState, shizukuState)
        }
        root.addView(backupButton)
        root.addView(button("Copy Backup Path") { copyBackupPath() })
        downloadPatchButton = button("Download & Verify Patch") {
            if (blockIfDisabled(downloadPatchButton, "Download & Verify Patch")) return@button
            lastAction = "Download & Verify Patch requested"
            patchPreparationController.preparePatchSafely()
        }
        root.addView(downloadPatchButton)
        applySafeButton = button("Apply Safe Config Preset") {
            refreshStatus()
            if (blockIfDisabled(applySafeButton, "Apply Safe Config Preset")) return@button
            pendingConfigPresetName = "Safe / Default"
            lastAction = "Apply Safe Config Preset requested"
            configPresetController.showSafeDefaultDryRun(gameState, shizukuState)
        }
        root.addView(applySafeButton)
        applyBalancedButton = button("Apply Balanced Preset") {
            refreshStatus()
            if (blockIfDisabled(applyBalancedButton, "Apply Balanced Preset")) return@button
            pendingConfigPresetName = "Balanced"
            lastAction = "Apply Balanced Preset requested"
            configPresetController.showBalancedDryRun(gameState, shizukuState, installedState)
        }
        root.addView(applyBalancedButton)
        root.addView(button("Update Vietnamese Patch") {
            openUrl(AppConstants.RELEASES_URL)
            lastAction = "Opened GitHub Releases"
            logger.add("Update check: opened GitHub Releases")
        })
        removePatchButton = button("Remove Vietnamese Patch") {
            refreshStatus()
            if (blockIfDisabled(removePatchButton, "Remove Vietnamese Patch")) return@button
            lastAction = "Remove Vietnamese Patch requested"
            patchPreparationController.showRemovePatchDryRun(gameState, shizukuState)
        }
        root.addView(removePatchButton)
        restoreButton = button("Restore Original Files") {
            refreshStatus()
            if (blockIfDisabled(restoreButton, "Restore Original Files")) return@button
            lastAction = "Restore Original Files opened"
            restoreFlowController.showRestoreSessions()
        }
        root.addView(restoreButton)
        root.addView(button("Check Game Folder") {
            refreshStatus()
            lastAction = "Check Game Folder"
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
        root.addView(button("Copy State Snapshot") { copyStateSnapshot() })
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
        gameInfo = gamePackageDetector.detectGlobalInfo(this)
        shizukuState = shizukuStateChecker.check(this)
        installedState = installedStateDetector.detect(this, gameInfo, gameState, shizukuState)
        actionState = HomeActionStateResolver.resolve(installedState, gameState, shizukuState)
        statusView?.text = statusRenderer.render(gameState, gameInfo, shizukuState, installedState)
        applyHomeActionState(actionState)
        logInstalledStateIfChanged(installedState, actionState)
    }

    private fun applyHomeActionState(state: HomeActionState?) {
        if (state == null) {
            return
        }
        installPatchButton.isEnabled = state.installPatchEnabled
        removePatchButton.isEnabled = state.removePatchEnabled
        applySafeButton.isEnabled = state.applySafeEnabled
        applyBalancedButton.isEnabled = state.applyBalancedEnabled
        restoreButton.isEnabled = state.restoreEnabled
        backupButton.isEnabled = state.backupEnabled
        downloadPatchButton.isEnabled = state.downloadPatchEnabled
    }

    private fun logInstalledStateIfChanged(
        state: InstalledState?,
        actions: HomeActionState?,
    ) {
        if (state == null || actions == null) {
            return
        }
        val signature = listOf(
            state.patchState,
            state.configState,
            state.pakExists,
            state.mountLangExists,
            state.mountLangPointsToPak,
            state.hasTrustedBackup,
            actions.primaryHint,
        ).joinToString("|")
        if (signature == lastStateSignature) {
            return
        }
        lastStateSignature = signature
        logger.add("State: patch=${state.patchState}, config=${state.configState}")
        logger.add("State: pak=${state.pakExists}, mountLang=${state.mountLangPointsToPak}")
        logger.add("State: trustedBackup=${state.hasTrustedBackup}")
        logger.add("Smart UI: ${actions.primaryHint}")
    }

    private fun detectInstalledStateSnapshot(): InstalledState {
        val currentGameState = gamePackageDetector.detect(this)
        val currentGameInfo = gamePackageDetector.detectGlobalInfo(this)
        val currentShizukuState = shizukuStateChecker.check(this)
        return installedStateDetector.detect(this, currentGameInfo, currentGameState, currentShizukuState)
    }

    private fun blockIfDisabled(button: Button, actionName: String): Boolean {
        if (button.isEnabled) {
            return false
        }
        val hint = actionState?.primaryHint ?: "Refresh status and complete setup first."
        showMessage("$actionName blocked", hint)
        logger.add("$actionName: blocked by smart state - $hint")
        return true
    }

    private fun openOrRequestShizuku() {
        lastAction = "Open Shizuku"
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
        intent.putExtra(
            Intent.EXTRA_TEXT,
            stateSnapshotText() + "\n\nDebug log:\n" + logger.text(),
        )
        startActivity(Intent.createChooser(intent, "Send Issue Report"))
        lastAction = "Issue report share opened"
        logger.add("Issue report: share sheet opened")
    }

    private fun copyStateSnapshot() {
        refreshStatus()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("WUWA VN state snapshot", stateSnapshotText()))
            Toast.makeText(this, "State snapshot copied.", Toast.LENGTH_SHORT).show()
            logger.add("State snapshot: copied")
        }
    }

    private fun stateSnapshotText(): String {
        val state = installedState
        return "App version: ${AppConstants.VERSION_NAME}\n" +
            "Game package: ${gameInfo?.packageName ?: AppConstants.GLOBAL_GAME_PACKAGE}\n" +
            "Game version: ${gameInfo?.versionName ?: "unknown"}\n" +
            "Shizuku: ${shizukuState.label}\n" +
            "Patch state: ${state?.patchState ?: "UNKNOWN"}\n" +
            "Config state: ${state?.configState ?: "UNKNOWN"}\n" +
            "Trusted backup: ${state?.hasTrustedBackup ?: false}\n" +
            "PAK exists: ${state?.pakExists ?: false}\n" +
            "MountLang points to PAK: ${state?.mountLangPointsToPak ?: false}\n" +
            "Engine.ini readable: ${state?.engineIniReadable ?: false}\n" +
            "DeviceProfiles.ini readable: ${state?.deviceProfilesReadable ?: false}\n" +
            "Last action: $lastAction"
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
