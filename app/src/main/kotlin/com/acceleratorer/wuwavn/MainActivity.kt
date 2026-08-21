package com.acceleratorer.wuwavn

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val logger = DebugLogger()
    private val gamePackageDetector = GamePackageDetector()
    private val shizukuStateChecker = ShizukuStateChecker(gamePackageDetector)
    private val rootAccessChecker = RootAccessChecker()
    private val backupManager = BackupManager()
    private val dryRunPlanner = PatchDryRunPlanner(backupManager)
    private val manifestRepository = PatchManifestRepository()
    private val shizukuFileSystem = ShizukuFileSystem()
    private val backupReader = ShizukuBackupReader(backupManager)
    private val gamePathDiagnosticReader = ShizukuGamePathDiagnosticReader()
    private val restoreDryRunPlanner = RestoreDryRunPlanner(backupManager)
    private val restoreWriter = ShizukuRestoreWriter()
    private val downloadClient = DownloadClient()
    private val trustedBackupFinder = TrustedBackupFinder(restoreDryRunPlanner)
    private val balancedPresetDryRunPlanner = BalancedPresetDryRunPlanner(
        trustedBackupFinder,
        ConfigPresetDiffPlanner(),
    )
    private val performancePresetDryRunPlanner = PerformancePresetDryRunPlanner(
        trustedBackupFinder,
        ConfigPresetDiffPlanner(),
    )
    private val installedStateDetector = InstalledStateDetector(
        ShizukuInstalledStateReader(),
        trustedBackupFinder,
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

    private val composeHomeUiState = mutableStateOf(ComposeHomeUiState.initial())
    private var shizukuState = ShizukuState.NOT_INSTALLED
    private var gameState = GamePackageDetector.State.NOT_INSTALLED
    private var gameInfo: GamePackageDetector.GameInfo? = null
    private var rootAccessState = RootAccessState.NOT_CHECKED
    private var installedState: InstalledState? = null
    private var actionState: HomeActionState? = null
    private var lastStateSignature: String? = null
    private var lastAction: String = "App started"
    private var pendingConfigPresetName: String? = null
    private var stateRefreshGeneration = 0
    @Volatile private var lastBackupPath: String? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        logger.add("Shizuku: binder received")
        refreshStatusFromShizukuCallback()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        logger.add("Shizuku: binder dead")
        refreshStatusFromShizukuCallback()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            logger.add("Shizuku permission result: $grantResult")
            refreshStatusFromShizukuCallback()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 17, 29)
        window.navigationBarColor = Color.rgb(11, 17, 29)

        initializeControllers()
        setContent {
            WuwaComposeTheme {
                ComposeHomeScreen(
                    uiState = composeHomeUiState.value,
                    callbacks = composeHomeCallbacks(),
                )
            }
        }
        logger.setListener { text ->
            runOnUiThread {
                composeHomeUiState.value = composeHomeUiState.value.copy(debugLogText = text)
            }
        }

        registerShizukuListeners()
        logger.add("App version: ${AppConstants.VERSION_NAME} (${AppConstants.VERSION_CODE})")
        logger.add("Android version: ${Build.VERSION.RELEASE}")
        refreshStatus()
        if (!OnboardingState.hasSeen(this)) {
            showOnboarding()
        }
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
            performancePresetDryRunPlanner = performancePresetDryRunPlanner,
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

    private fun composeHomeCallbacks(): ComposeHomeCallbacks = ComposeHomeCallbacks(
        onShowSetupGuide = { showOnboarding() },
        onInstallHelp = { showInstallHelp() },
        onShizukuSetupHelp = { showShizukuHelp() },
        onOpenShizuku = { openOrRequestShizuku() },
        onOpenDeveloperOptions = { openDeveloperOptions() },
        onRootPreviewHelp = { showRootPreviewHelp() },
        onCheckRootAccess = { checkRootAccessPreview() },
        onCheckGameFolder = { checkGameFolder() },
        onShowPatchPlan = { showPatchPlan() },
        onBackupGameConfigs = { backupGameConfigs() },
        onCopyBackupPath = { copyBackupPath() },
        onDownloadPatch = { downloadPatch() },
        onInstallPatch = { installVietnamesePatch() },
        onUpdatePatch = { openUpdatePage() },
        onRemovePatch = { removeVietnamesePatch() },
        onRestoreOriginal = { restoreOriginalFiles() },
        onApplySafe = { applySafePreset() },
        onApplyBalanced = { applyBalancedPreset() },
        onApplyPerformance = { applyPerformancePreset() },
        onCopyDebugLog = { copyLog() },
        onCopyStateSnapshot = { copyStateSnapshot() },
        onRecoveryGuide = { showRecoveryGuide() },
        onSendIssueReport = { shareLog() },
        onMoreTools = { showMoreTools() },
    )

    private fun showMoreTools() {
        val labels = arrayOf(
            "Install Help",
            "Current State",
            "Shizuku Setup Help",
            "Open Developer Options",
            "Check Game Folder",
            "Game Path Diagnostic",
            "Show Patch Plan",
            "Copy Backup Path",
            "Update Vietnamese Patch",
            "Remove Vietnamese Patch",
            "Restore Original Files",
            "Apply Safe Config Preset",
            "Apply Balanced Preset",
            "Apply Performance Preset",
            "Recovery Guide",
            "Root Preview Help",
            "Check Root Access",
            "Copy State Snapshot",
            "Send Issue Report",
            "Copy Debug Log",
        )

        dialogs.showSelection("More Tools", labels) { which ->
            when (labels[which]) {
                "Install Help" -> showInstallHelp()
                "Current State" -> showCurrentState()
                "Shizuku Setup Help" -> showShizukuHelp()
                "Open Developer Options" -> openDeveloperOptions()
                "Check Game Folder" -> checkGameFolder()
                "Game Path Diagnostic" -> showGamePathDiagnostic()
                "Show Patch Plan" -> showPatchPlan()
                "Copy Backup Path" -> copyBackupPath()
                "Update Vietnamese Patch" -> openUpdatePage()
                "Remove Vietnamese Patch" -> removeVietnamesePatch()
                "Restore Original Files" -> restoreOriginalFiles()
                "Apply Safe Config Preset" -> applySafePreset()
                "Apply Balanced Preset" -> applyBalancedPreset()
                "Apply Performance Preset" -> applyPerformancePreset()
                "Recovery Guide" -> showRecoveryGuide()
                "Root Preview Help" -> showRootPreviewHelp()
                "Check Root Access" -> checkRootAccessPreview()
                "Copy State Snapshot" -> copyStateSnapshot()
                "Send Issue Report" -> shareLog()
                "Copy Debug Log" -> copyLog()
            }
        }
        lastAction = "Opened More Tools"
        logger.add("More tools: shown")
    }

    private fun showCurrentState() {
        refreshStatus()
        dialogs.showMessage("Current State", composeHomeUiState.value.statusText)
        lastAction = "Opened Current State"
        logger.add("Current state: shown")
    }

    private fun showInstallHelp() {
        dialogs.showMessage(
            "Install Help",
            "For normal users:\n\n" +
                "1. Download only WUWA-VN-v${AppConstants.VERSION_NAME}-release.apk from GitHub Releases.\n" +
                "2. Do not install Source code zip/tar.gz files.\n" +
                "3. Android 11 or newer is required. On BlueStacks, use Android 11 64-bit.\n" +
                "4. If Android asks, allow Install unknown apps for your browser or file manager.\n" +
                "5. If install fails, uninstall the old WUWA VN app first, then install again.\n" +
                "6. Open WUWA VN, install/start Shizuku, then follow Start Setup.",
        )
        lastAction = "Opened Install Help"
        logger.add("Install help: shown")
    }

    private fun checkGameFolder() {
        refreshStatus()
        lastAction = "Check Game Folder"
        logger.add("Game folder: checked package state")
    }

    private fun showGamePathDiagnostic() {
        val detectedGameState = gamePackageDetector.detect(this)
        val detectedGameInfo = gamePackageDetector.detectGlobalInfo(this)
        val detectedShizukuState = shizukuStateChecker.check(this)
        if (detectedGameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            dialogs.showMessage(
                "Game Path Diagnostic blocked",
                "Wuthering Waves Global is not detected. Install or open the Global version first.",
            )
            logger.add("Game path diagnostic: blocked - game missing")
            return
        }
        if (detectedShizukuState != ShizukuState.READY) {
            dialogs.showMessage("Game Path Diagnostic blocked", shizukuFileSystem.disabledReason(detectedShizukuState))
            logger.add("Game path diagnostic: blocked - Shizuku not ready")
            return
        }

        lastAction = "Game Path Diagnostic requested"
        logger.add("Game path diagnostic: started")
        Toast.makeText(this, "Reading game paths...", Toast.LENGTH_SHORT).show()

        Thread {
            val report = gamePathDiagnosticReader.read(applicationContext)
            val text = GamePathDiagnosticRenderer.render(report, detectedGameInfo, detectedShizukuState)

            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                dialogs.showConfirmation(
                    title = "Game Path Diagnostic",
                    message = text,
                    positiveLabel = "Copy Report",
                ) {
                    copyGamePathDiagnostic(text)
                }
                logger.add("Game path diagnostic: shown")
            }
        }.apply {
            name = "WUWA-PathDiagnostic"
            start()
        }
    }

    private fun showPatchPlan() {
        refreshStatus()
        lastAction = "Show Patch Plan"
        patchPreparationController.showPatchDryRun(gameState, shizukuState)
    }

    private fun backupGameConfigs() {
        refreshStatus()
        if (blockIfActionDisabled(actionState?.backupEnabled == true, "Backup Game Configs")) return
        lastAction = "Backup Game Configs requested"
        backupFlowController.backupGameConfigs(gameState, shizukuState)
    }

    private fun downloadPatch() {
        if (blockIfActionDisabled(actionState?.downloadPatchEnabled == true, "Download & Verify Patch")) return
        lastAction = "Download & Verify Patch requested"
        patchPreparationController.preparePatchSafely()
    }

    private fun installVietnamesePatch() {
        refreshStatus()
        if (blockIfActionDisabled(actionState?.installPatchEnabled == true, "Install Vietnamese Patch")) return
        lastAction = "Install Vietnamese Patch requested"
        patchPreparationController.showPatchWriteDryRun(gameState, shizukuState)
    }

    private fun openUpdatePage() {
        openUrl(AppConstants.RELEASES_URL)
        lastAction = "Opened GitHub Releases"
        logger.add("Update check: opened GitHub Releases")
    }

    private fun removeVietnamesePatch() {
        refreshStatus()
        if (blockIfActionDisabled(actionState?.removePatchEnabled == true, "Remove Vietnamese Patch")) return
        lastAction = "Remove Vietnamese Patch requested"
        patchPreparationController.showRemovePatchDryRun(gameState, shizukuState)
    }

    private fun restoreOriginalFiles() {
        refreshStatus()
        lastAction = "Restore Original Files opened"
        restoreFlowController.showRestoreSessions()
    }

    private fun applySafePreset() {
        refreshStatus()
        pendingConfigPresetName = "Safe / Default"
        lastAction = "Apply Safe Config Preset requested"
        configPresetController.showSafeDefaultDryRun(gameState, shizukuState)
    }

    private fun applyBalancedPreset() {
        refreshStatus()
        pendingConfigPresetName = "Balanced"
        lastAction = "Apply Balanced Preset requested"
        configPresetController.showBalancedDryRun(gameState, shizukuState, installedState)
    }

    private fun applyPerformancePreset() {
        refreshStatus()
        pendingConfigPresetName = "Performance"
        lastAction = "Apply Performance Preset requested"
        configPresetController.showPerformanceDryRun(gameState, shizukuState, installedState)
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
        val detectedGameState = gamePackageDetector.detect(this)
        val detectedGameInfo = gamePackageDetector.detectGlobalInfo(this)
        val detectedShizukuState = shizukuStateChecker.check(this)
        val visibleInstalledState = installedState.takeIf {
            detectedGameState == GamePackageDetector.State.GLOBAL_INSTALLED &&
                detectedShizukuState == ShizukuState.READY
        }

        gameState = detectedGameState
        gameInfo = detectedGameInfo
        shizukuState = detectedShizukuState
        installedState = visibleInstalledState
        renderStatusSnapshot(visibleInstalledState)
        refreshInstalledStateInBackground(detectedGameInfo, detectedGameState, detectedShizukuState)
    }

    private fun refreshStatusFromShizukuCallback() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshStatus()
        } else {
            runOnUiThread { refreshStatus() }
        }
    }

    private fun renderStatusSnapshot(state: InstalledState?) {
        val resolvedActionState = HomeActionStateResolver.resolve(state, gameState, shizukuState)
        actionState = resolvedActionState
        composeHomeUiState.value = composeHomeUiState.value.copy(
            statusText = statusRenderer.render(gameState, gameInfo, shizukuState, state),
            setupChecklistText = setupChecklistText(),
            rootPreviewText = RootPreviewRenderer.render(rootAccessState),
            diagnosticsSummaryText = diagnosticsSummaryText(state, resolvedActionState),
            snapshotPreviewText = snapshotPreviewText(resolvedActionState),
            debugLogText = logger.text(),
            actionState = resolvedActionState,
        )
    }

    private fun refreshInstalledStateInBackground(
        gameInfoSnapshot: GamePackageDetector.GameInfo?,
        gameStateSnapshot: GamePackageDetector.State,
        shizukuStateSnapshot: ShizukuState,
    ) {
        val refreshId = ++stateRefreshGeneration
        Thread {
            val detectedState = try {
                installedStateDetector.detect(applicationContext, gameInfoSnapshot, gameStateSnapshot, shizukuStateSnapshot)
            } catch (throwable: Throwable) {
                logger.add("State detection failed: ${throwable.message}")
                unavailableInstalledState(gameInfoSnapshot, "State detection failed: ${throwable.message}")
            }

            runOnUiThread {
                if (refreshId != stateRefreshGeneration || isFinishing || isDestroyed) {
                    return@runOnUiThread
                }

                gameState = gameStateSnapshot
                gameInfo = gameInfoSnapshot
                shizukuState = shizukuStateSnapshot
                installedState = detectedState
                renderStatusSnapshot(detectedState)
                logInstalledStateIfChanged(detectedState, actionState)
            }
        }.apply {
            name = "WUWA-StateRefresh"
            start()
        }
    }

    private fun unavailableInstalledState(
        gameInfoSnapshot: GamePackageDetector.GameInfo?,
        diagnostic: String,
    ): InstalledState = InstalledState(
        patchState = PatchInstallState.UNKNOWN,
        configState = ConfigInstallState.UNKNOWN,
        pakExists = false,
        mountLangExists = false,
        mountLangPointsToPak = false,
        engineIniReadable = false,
        deviceProfilesReadable = false,
        hasTrustedBackup = false,
        trustedBackupPath = null,
        gameVersionName = gameInfoSnapshot?.versionName,
        supportedGameVersion = AppConstants.SUPPORTED_GAME_VERSION,
        diagnostics = listOf(diagnostic),
    )

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
            actions.applyPerformanceEnabled,
            actions.primaryHint,
        ).joinToString("|")
        if (signature == lastStateSignature) {
            return
        }
        lastStateSignature = signature
        logger.add("State: patch=${state.patchState}, config=${state.configState}")
        logger.add(
            "State: pak=${readableStateValue(state, state.pakExists)}, " +
                "mountLang=${readableStateValue(state, state.mountLangPointsToPak)}",
        )
        logger.add("State: trustedBackup=${state.hasTrustedBackup}")
        logger.add("Smart UI: ${actions.primaryHint}")
    }

    private fun detectInstalledStateSnapshot(): InstalledState {
        val currentGameState = gamePackageDetector.detect(this)
        val currentGameInfo = gamePackageDetector.detectGlobalInfo(this)
        val currentShizukuState = shizukuStateChecker.check(this)
        return installedStateDetector.detect(this, currentGameInfo, currentGameState, currentShizukuState)
    }

    private fun blockIfActionDisabled(enabled: Boolean, actionName: String): Boolean {
        if (enabled) {
            return false
        }
        val hint = actionState?.primaryHint ?: "Refresh status and complete setup first."
        showMessage("$actionName blocked", hint)
        logger.add("$actionName: blocked by smart state - $hint")
        return true
    }

    private fun showOnboarding() {
        lastAction = "Opened Setup Guide"
        logger.add("Onboarding: shown")
        dialogs.showConfirmation(
            title = "WUWA VN Setup",
            message = OnboardingRenderer.render(),
            positiveLabel = "Got it",
        ) {
            OnboardingState.markSeen(this)
            logger.add("Onboarding: completed")
        }
    }

    private fun showShizukuHelp() {
        refreshStatus()
        val message = when (shizukuState) {
            ShizukuState.NOT_INSTALLED ->
                "Shizuku is not installed.\n\nInstall Shizuku first, then start it using Wireless Debugging."
            ShizukuState.INSTALLED_NOT_RUNNING ->
                "Shizuku is installed but not running.\n\nOpen Shizuku and start the service."
            ShizukuState.RUNNING_PERMISSION_DENIED ->
                "Shizuku is running but permission is not granted.\n\nGrant permission to WUWA VN in Shizuku."
            ShizukuState.READY ->
                "Shizuku is READY.\n\nYou can backup, inspect dry-runs, install/update, and remove the verified WUWA 3.6 patch. Config preset and general restore writes are locked in this release."
        }

        dialogs.showMessage("Shizuku Setup Help", message)
        lastAction = "Opened Shizuku Setup Help"
        logger.add("Shizuku help: shown")
    }

    private fun showRootPreviewHelp() {
        dialogs.showMessage("Root Backend Preview", RootPreviewRenderer.help(rootAccessState))
        lastAction = "Opened Root Preview Help"
        logger.add("Root preview help: shown")
    }

    private fun checkRootAccessPreview() {
        if (rootAccessState == RootAccessState.CHECKING) {
            Toast.makeText(this, "Root check is already running.", Toast.LENGTH_SHORT).show()
            return
        }

        dialogs.showConfirmation(
            title = "Check Root Access",
            message = "This preview may show a root manager popup.\n\nNo files will be changed. Root writes are disabled in this version.\n\nFor normal users, Shizuku is still recommended.",
            positiveLabel = "Check Root",
        ) {
            startRootAccessPreviewCheck()
        }
    }

    private fun startRootAccessPreviewCheck() {
        rootAccessState = RootAccessState.CHECKING
        lastAction = "Root access preview requested"
        logger.add("Root preview: checking")
        renderStatusSnapshot(installedState)

        Thread {
            val result = rootAccessChecker.check()
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                rootAccessState = result
                lastAction = "Root preview: ${result.label}"
                renderStatusSnapshot(installedState)
                dialogs.showMessage("Root Backend Preview", RootPreviewRenderer.result(result))
                logger.add("Root preview: ${result.label}")
            }
        }.apply {
            name = "WUWA-RootPreview"
            start()
        }
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            lastAction = "Opened Developer Options"
            logger.add("Developer Options: opened")
        } catch (exception: Exception) {
            openUrl("https://shizuku.rikka.app/guide/setup/")
            lastAction = "Opened Shizuku setup guide"
            logger.add("Developer Options unavailable, opened Shizuku guide")
        }
    }

    private fun setupChecklistText(): String = buildString {
        appendLine("Setup Checklist")
        appendLine("Game installed: ${if (gameState == GamePackageDetector.State.GLOBAL_INSTALLED) "OK" else "Missing"}")
        appendLine("Shizuku ready: ${if (shizukuState == ShizukuState.READY) "OK" else "Not ready"}")
        appendLine("Root preview: ${rootAccessState.label}")
        appendLine("Trusted backup: ${if (installedState?.hasTrustedBackup == true) "OK" else "Missing"}")
        appendLine("Patch state: ${installedState?.patchState ?: "UNKNOWN"}")
        appendLine("Ready to install patch: ${isReadyToInstallPatchLabel()}")
        appendLine("Ready to apply presets: ${isReadyToApplyPresetsLabel()}")
    }

    private fun isReadyToInstallPatchLabel(): String =
        if (
            gameState == GamePackageDetector.State.GLOBAL_INSTALLED &&
            shizukuState == ShizukuState.READY &&
            installedState?.hasTrustedBackup == true &&
            installedState?.patchState == PatchInstallState.ORIGINAL
        ) {
            "YES"
        } else {
            "NO"
        }

    private fun isReadyToApplyPresetsLabel(): String =
        if (
            gameState == GamePackageDetector.State.GLOBAL_INSTALLED &&
            shizukuState == ShizukuState.READY &&
            installedState?.hasTrustedBackup == true &&
            installedState?.patchState == PatchInstallState.PATCHED
        ) {
            "YES"
        } else {
            "NO"
        }

    private fun diagnosticsSummaryText(state: InstalledState?, actions: HomeActionState): String = buildString {
        appendLine("App version: ${AppConstants.VERSION_NAME} (${AppConstants.VERSION_CODE})")
        appendLine("Game package: ${gameInfo?.packageName ?: AppConstants.GLOBAL_GAME_PACKAGE}")
        appendLine("Game version: ${gameInfo?.versionName ?: "unknown"}")
        appendLine("Supported game version: ${AppConstants.SUPPORTED_GAME_VERSION}")
        appendLine("Shizuku: ${shizukuState.label}")
        appendLine("Root preview: ${rootAccessState.label}")
        appendLine("Patch state: ${state?.patchState ?: PatchInstallState.UNKNOWN}")
        appendLine("Config state: ${state?.configState ?: ConfigInstallState.UNKNOWN}")
        appendLine("Trusted backup: ${state?.hasTrustedBackup ?: false}")
        appendLine("Hint: ${actions.primaryHint}")
    }

    private fun snapshotPreviewText(actions: HomeActionState): String = buildString {
        appendLine("Preset write policy:")
        appendLine("Safe: ${ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.SAFE_DEFAULT)}")
        appendLine("Balanced: ${ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.BALANCED)}")
        appendLine("Performance: ${ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.PERFORMANCE)}")
        appendLine("Max Graphics: ${ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.MAX_GRAPHICS)}")
        appendLine()
        appendLine("Root backend preview: ${rootAccessState.label}")
        appendLine("Root write enabled: false")
        appendLine()
        appendLine("Actions:")
        appendLine("Install Patch: ${actions.installPatchEnabled}")
        appendLine("Apply Safe: ${actions.applySafeEnabled}")
        appendLine("Apply Balanced: ${actions.applyBalancedEnabled}")
        appendLine("Apply Performance: ${actions.applyPerformanceEnabled}")
        appendLine("Remove Patch: ${actions.removePatchEnabled}")
        appendLine("Restore Original: ${actions.restoreEnabled}")
        appendLine("Backup Configs: ${actions.backupEnabled}")
        appendLine("Download Patch: ${actions.downloadPatchEnabled}")
        appendLine("Last action: $lastAction")
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

    private fun copyGamePathDiagnostic(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("WUWA VN game path diagnostic", text))
            Toast.makeText(this, "Game path diagnostic copied.", Toast.LENGTH_SHORT).show()
            lastAction = "Copied Game Path Diagnostic"
            logger.add("Game path diagnostic: copied")
        }
    }

    private fun shareLog() {
        refreshStatus()
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
        val actions = actionState
        return buildString {
            appendLine("WUWA VN State Snapshot")
            appendLine("App version: ${AppConstants.VERSION_NAME} (${AppConstants.VERSION_CODE})")
            appendLine("Game package: ${gameInfo?.packageName ?: AppConstants.GLOBAL_GAME_PACKAGE}")
            appendLine("Game version: ${gameInfo?.versionName ?: "unknown"}")
            appendLine("Launcher compatibility: WUWA Global ${AppConstants.SUPPORTED_GAME_VERSION}")
            appendLine("Supported game version: ${AppConstants.SUPPORTED_GAME_VERSION}")
            appendLine()
            appendLine("Preset write policy:")
            appendLine("Safe: ${ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.SAFE_DEFAULT)}")
            appendLine("Balanced: ${ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.BALANCED)}")
            appendLine("Performance: ${ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.PERFORMANCE)}")
            appendLine("Max Graphics: ${ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.MAX_GRAPHICS)}")
            appendLine()
            appendLine("Root backend preview: ${rootAccessState.label}")
            appendLine("Root write enabled: false")
            appendLine()
            appendLine("Shizuku: ${shizukuState.label}")
            appendLine()

            if (state == null) {
                appendLine("Installed state: unavailable")
            } else {
                appendLine("Patch state: ${state.patchState}")
                appendLine("Config state: ${state.configState}")
                appendLine("Trusted backup: ${state.hasTrustedBackup}")
                appendLine("PAK exists: ${readableStateValue(state, state.pakExists)}")
                appendLine("MountLang exists: ${readableStateValue(state, state.mountLangExists)}")
                appendLine("MountLang points to PAK: ${readableStateValue(state, state.mountLangPointsToPak)}")
                appendLine("Engine.ini readable: ${readableStateValue(state, state.engineIniReadable)}")
                appendLine("DeviceProfiles.ini readable: ${readableStateValue(state, state.deviceProfilesReadable)}")
            }

            appendLine()
            appendLine("Actions:")
            if (actions == null) {
                appendLine("Action state: unavailable")
            } else {
                appendLine("Install Patch: ${actions.installPatchEnabled}")
                appendLine("Apply Safe: ${actions.applySafeEnabled}")
                appendLine("Apply Balanced: ${actions.applyBalancedEnabled}")
                appendLine("Apply Performance: ${actions.applyPerformanceEnabled}")
                appendLine("Remove Patch: ${actions.removePatchEnabled}")
                appendLine("Restore Original: ${actions.restoreEnabled}")
                appendLine("Backup Configs: ${actions.backupEnabled}")
                appendLine("Download Patch: ${actions.downloadPatchEnabled}")
                appendLine("Hint: ${actions.primaryHint}")
            }
            appendLine("Last action: $lastAction")
        }
    }

    private fun readableStateValue(state: InstalledState, value: Boolean): String =
        if (state.patchState == PatchInstallState.UNKNOWN) {
            "unknown"
        } else {
            value.toString()
        }

    private fun showRecoveryGuide() {
        refreshStatus()
        val message = when (installedState?.patchState) {
            PatchInstallState.ORIGINAL ->
                "Original state detected.\n\nRecommended:\n" +
                    "1. Run Backup Game Configs.\n" +
                    "2. Download & Verify Patch.\n" +
                    "3. Install Vietnamese Patch only if the button is enabled.\n" +
                    "4. If install stays disabled, open More Tools > Game Path Diagnostic and send the report.\n" +
                    "5. Config preset writes are locked in WUWA 3.6; use patch install/update or remove transaction only."
            PatchInstallState.PATCHED ->
                "Patched state detected.\n\nAvailable:\n" +
                    "1. Update/reinstall Vietnamese Patch.\n" +
                    "2. Remove Vietnamese Patch transactionally.\n" +
                    "3. Inspect config/preset dry-runs; config writes are locked in WUWA 3.6.\n" +
                    "4. Inspect Restore Original Files dry-run; restore write is locked."
            PatchInstallState.PARTIAL ->
                "Partial state detected.\n\nRecommended recovery:\n" +
                    "1. Do not apply config presets.\n" +
                    "2. Use Remove Vietnamese Patch if available.\n" +
                    "3. Inspect Restore Original Files dry-run; restore write is locked in WUWA 3.6.\n" +
                    "4. Refresh state after recovery."
            PatchInstallState.UNKNOWN, null ->
                "State unknown.\n\nRecommended:\n" +
                    "1. Check Shizuku is running.\n" +
                    "2. Grant Shizuku permission.\n" +
                    "3. Check Game Folder.\n" +
                    "4. Inspect the trusted backup dry-run; restore write is locked in WUWA 3.6."
        }

        dialogs.showMessage("Recovery Guide", message)
        lastAction = "Opened Recovery Guide"
        logger.add("Recovery guide: shown")
    }

    private fun showMessage(title: String, message: String) {
        dialogs.showMessage(title, message)
    }
}
