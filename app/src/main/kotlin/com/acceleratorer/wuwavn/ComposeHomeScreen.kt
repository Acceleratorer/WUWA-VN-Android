package com.acceleratorer.wuwavn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ComposeHomeUiState(
    val statusText: String,
    val setupChecklistText: String,
    val diagnosticsSummaryText: String,
    val snapshotPreviewText: String,
    val debugLogText: String,
    val actionState: HomeActionState?,
) {
    companion object {
        fun initial(): ComposeHomeUiState = ComposeHomeUiState(
            statusText = "Loading status...",
            setupChecklistText = "Setup Checklist\nRefreshing...",
            diagnosticsSummaryText = "Diagnostics\nRefreshing...",
            snapshotPreviewText = "State Snapshot Preview\nRefreshing...",
            debugLogText = "",
            actionState = null,
        )
    }
}

data class ComposeHomeCallbacks(
    val onShowSetupGuide: () -> Unit,
    val onInstallHelp: () -> Unit,
    val onShizukuSetupHelp: () -> Unit,
    val onOpenShizuku: () -> Unit,
    val onOpenDeveloperOptions: () -> Unit,
    val onCheckGameFolder: () -> Unit,
    val onShowPatchPlan: () -> Unit,
    val onBackupGameConfigs: () -> Unit,
    val onCopyBackupPath: () -> Unit,
    val onDownloadPatch: () -> Unit,
    val onInstallPatch: () -> Unit,
    val onUpdatePatch: () -> Unit,
    val onRemovePatch: () -> Unit,
    val onRestoreOriginal: () -> Unit,
    val onApplySafe: () -> Unit,
    val onApplyBalanced: () -> Unit,
    val onApplyPerformance: () -> Unit,
    val onCopyDebugLog: () -> Unit,
    val onCopyStateSnapshot: () -> Unit,
    val onRecoveryGuide: () -> Unit,
    val onSendIssueReport: () -> Unit,
)

@Composable
fun WuwaComposeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF2563EB),
            secondary = Color(0xFF16A34A),
            background = Color(0xFFF8FAFC),
            surface = Color.White,
            onSurface = Color(0xFF172033),
        ),
        content = content,
    )
}

@Composable
fun ComposeHomeScreen(
    uiState: ComposeHomeUiState,
    callbacks: ComposeHomeCallbacks,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeaderBlock()
            HeroBlock()
            InstallHelpBlock(callbacks)
            TextPanel(title = "Current State", body = uiState.statusText)
            TextPanel(title = "Setup Checklist", body = uiState.setupChecklistText)
            TextPanel(title = "Diagnostics Summary", body = uiState.diagnosticsSummaryText)
            TextPanel(title = "State Snapshot Preview", body = uiState.snapshotPreviewText, monospace = true)
            SetupSection(callbacks)
            PatchSection(uiState.actionState, callbacks)
            ConfigPresetSection(uiState.actionState, callbacks)
            SafetyBlock()
            TextPanel(title = "Debug Log", body = uiState.debugLogText.ifBlank { "No log entries yet." }, monospace = true)
            DiagnosticsSection(callbacks)
        }
    }
}

@Composable
private fun HeaderBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "WUWA VN Android",
            color = Color(0xFF172033),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Safe patch manager for Vietnamese Wuthering Waves players.",
            color = Color(0xFF516070),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun HeroBlock() {
    Image(
        painter = painterResource(R.drawable.phrolova_header),
        contentDescription = "WUWA VN header artwork",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp),
    )
}

@Composable
private fun InstallHelpBlock(callbacks: ComposeHomeCallbacks) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Easy Install", color = Color(0xFF1E3A8A), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                text = "Use the release APK, Android 11+, then start Shizuku before patch operations.",
                color = Color(0xFF334155),
                fontSize = 13.sp,
            )
            OutlinedButton(onClick = callbacks.onInstallHelp) {
                Text("Install Help")
            }
        }
    }
}

@Composable
private fun TextPanel(
    title: String,
    body: String,
    monospace: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Color(0xFF172033), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                text = body,
                color = Color(0xFF334155),
                fontSize = 13.sp,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SetupSection(callbacks: ComposeHomeCallbacks) {
    ActionSection(
        title = "Setup",
        actions = listOf(
            HomeAction("Show Setup Guide", true, callbacks.onShowSetupGuide),
            HomeAction("Install Help", true, callbacks.onInstallHelp),
            HomeAction("Shizuku Setup Help", true, callbacks.onShizukuSetupHelp),
            HomeAction("Open Shizuku", true, callbacks.onOpenShizuku),
            HomeAction("Open Developer Options", true, callbacks.onOpenDeveloperOptions),
            HomeAction("Check Game Folder", true, callbacks.onCheckGameFolder),
        ),
    )
}

@Composable
private fun PatchSection(
    actionState: HomeActionState?,
    callbacks: ComposeHomeCallbacks,
) {
    ActionSection(
        title = "Patch",
        actions = listOf(
            HomeAction("Show Patch Plan", true, callbacks.onShowPatchPlan),
            HomeAction("Backup Game Configs", actionState?.backupEnabled == true, callbacks.onBackupGameConfigs),
            HomeAction("Copy Backup Path", true, callbacks.onCopyBackupPath),
            HomeAction("Download & Verify Patch", actionState?.downloadPatchEnabled != false, callbacks.onDownloadPatch),
            HomeAction("Install Vietnamese Patch", actionState?.installPatchEnabled == true, callbacks.onInstallPatch, primary = true),
            HomeAction("Update Vietnamese Patch", true, callbacks.onUpdatePatch),
            HomeAction("Remove Vietnamese Patch", actionState?.removePatchEnabled == true, callbacks.onRemovePatch),
            HomeAction("Restore Original Files", actionState?.restoreEnabled == true, callbacks.onRestoreOriginal),
        ),
    )
}

@Composable
private fun ConfigPresetSection(
    actionState: HomeActionState?,
    callbacks: ComposeHomeCallbacks,
) {
    ActionSection(
        title = "Config Presets",
        actions = listOf(
            HomeAction("Apply Safe Config Preset", actionState?.applySafeEnabled == true, callbacks.onApplySafe),
            HomeAction("Apply Balanced Preset", actionState?.applyBalancedEnabled == true, callbacks.onApplyBalanced),
            HomeAction("Apply Performance Preset", actionState?.applyPerformanceEnabled == true, callbacks.onApplyPerformance),
        ),
    )
}

@Composable
private fun DiagnosticsSection(callbacks: ComposeHomeCallbacks) {
    ActionSection(
        title = "Diagnostics",
        actions = listOf(
            HomeAction("Copy Debug Log", true, callbacks.onCopyDebugLog),
            HomeAction("Copy State Snapshot", true, callbacks.onCopyStateSnapshot),
            HomeAction("Recovery Guide", true, callbacks.onRecoveryGuide),
            HomeAction("Send Issue Report", true, callbacks.onSendIssueReport),
        ),
    )
}

@Composable
private fun SafetyBlock() {
    TextPanel(
        title = "Safety Rules",
        body = "Only allowlisted WUWA targets can be planned:\n" +
            "- Engine.ini\n" +
            "- DeviceProfiles.ini\n" +
            "- MountLang_en.txt\n" +
            "- WuWaVH_99_P.pak\n\n" +
            "Max Graphics remains locked in v3.3.18.\n\n" +
            "Always backup first. Never use this app for cheating, anti-cheat bypass, or gameplay manipulation.",
    )
}

private data class HomeAction(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val primary: Boolean = false,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActionSection(title: String, actions: List<HomeAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFF2563EB), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEach { action ->
                ActionButton(action)
            }
        }
    }
}

@Composable
private fun ActionButton(action: HomeAction) {
    if (action.primary) {
        Button(
            onClick = action.onClick,
            enabled = action.enabled,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
        ) {
            Text(action.label)
        }
    } else {
        OutlinedButton(
            onClick = action.onClick,
            enabled = action.enabled,
        ) {
            Text(action.label)
        }
    }
}

@Preview(name = "WUWA Home - Patched", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WuwaPatchedPreview() {
    WuwaComposeTheme {
        ComposeHomeScreen(
            uiState = ComposeHomeUiState(
                statusText = "Game package: com.kurogame.wutheringwaves.global\n" +
                    "Game version: 3.3.1\n" +
                    "Shizuku: Ready\n" +
                    "Patch state: PATCHED\n" +
                    "Config state: PERFORMANCE",
                setupChecklistText = "Game installed: OK\n" +
                    "Shizuku ready: OK\n" +
                    "Trusted backup: OK\n" +
                    "Patch state: PATCHED\n" +
                    "Ready to install patch: NO\n" +
                    "Ready to apply presets: YES",
                diagnosticsSummaryText = "App version: 3.3.18 (52)\n" +
                    "Supported game version: 3.3\n" +
                    "Shizuku: Ready\n" +
                    "Patch state: PATCHED\n" +
                    "Trusted backup: true\n" +
                    "Hint: Vietnamese patch appears installed. Safe, Balanced, Performance, Remove, or Restore is available.",
                snapshotPreviewText = "Preset write policy:\n" +
                    "Safe: WRITE_ENABLED\n" +
                    "Balanced: WRITE_ENABLED\n" +
                    "Performance: WRITE_ENABLED\n" +
                    "Max Graphics: LOCKED\n\n" +
                    "Actions:\n" +
                    "Install Patch: false\n" +
                    "Apply Safe: true\n" +
                    "Apply Balanced: true\n" +
                    "Apply Performance: true\n" +
                    "Remove Patch: true\n" +
                    "Restore Original: true\n" +
                    "Last action: App started",
                debugLogText = "[12:00:00] Smart UI: Safe, Balanced, Performance, Remove, or Restore is available.",
                actionState = previewActionState(patched = true),
            ),
            callbacks = previewCallbacks(),
        )
    }
}

@Preview(name = "WUWA Home - Setup Blocked", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WuwaSetupBlockedPreview() {
    WuwaComposeTheme {
        ComposeHomeScreen(
            uiState = ComposeHomeUiState(
                statusText = "Game package: com.kurogame.wutheringwaves.global\n" +
                    "Game version: 3.3.1\n" +
                    "Shizuku: Shizuku installed but not running\n" +
                    "Patch state: UNKNOWN\n" +
                    "Config state: UNKNOWN",
                setupChecklistText = "Game installed: OK\n" +
                    "Shizuku ready: Not ready\n" +
                    "Trusted backup: Missing\n" +
                    "Patch state: UNKNOWN\n" +
                    "Ready to install patch: NO\n" +
                    "Ready to apply presets: NO",
                diagnosticsSummaryText = "App version: 3.3.18 (52)\n" +
                    "Supported game version: 3.3\n" +
                    "Shizuku: Shizuku installed but not running\n" +
                    "Patch state: UNKNOWN\n" +
                    "Trusted backup: false\n" +
                    "Hint: Complete game/Shizuku setup before file operations.",
                snapshotPreviewText = "Preset write policy:\n" +
                    "Safe: WRITE_ENABLED\n" +
                    "Balanced: WRITE_ENABLED\n" +
                    "Performance: WRITE_ENABLED\n" +
                    "Max Graphics: LOCKED\n\n" +
                    "Actions:\n" +
                    "Install Patch: false\n" +
                    "Apply Safe: false\n" +
                    "Apply Balanced: false\n" +
                    "Apply Performance: false\n" +
                    "Remove Patch: false\n" +
                    "Restore Original: false\n" +
                    "Last action: App started",
                debugLogText = "[12:00:00] Smart UI: Complete game/Shizuku setup before file operations.",
                actionState = previewActionState(patched = false),
            ),
            callbacks = previewCallbacks(),
        )
    }
}

private fun previewActionState(patched: Boolean): HomeActionState = HomeActionState(
    installPatchEnabled = false,
    removePatchEnabled = patched,
    applySafeEnabled = patched,
    applyBalancedEnabled = patched,
    applyPerformanceEnabled = patched,
    restoreEnabled = patched,
    backupEnabled = patched,
    downloadPatchEnabled = true,
    primaryHint = if (patched) {
        "Safe, Balanced, Performance, Remove, or Restore is available."
    } else {
        "Complete game/Shizuku setup before file operations."
    },
)

private fun previewCallbacks(): ComposeHomeCallbacks = ComposeHomeCallbacks(
    onShowSetupGuide = {},
    onInstallHelp = {},
    onShizukuSetupHelp = {},
    onOpenShizuku = {},
    onOpenDeveloperOptions = {},
    onCheckGameFolder = {},
    onShowPatchPlan = {},
    onBackupGameConfigs = {},
    onCopyBackupPath = {},
    onDownloadPatch = {},
    onInstallPatch = {},
    onUpdatePatch = {},
    onRemovePatch = {},
    onRestoreOriginal = {},
    onApplySafe = {},
    onApplyBalanced = {},
    onApplyPerformance = {},
    onCopyDebugLog = {},
    onCopyStateSnapshot = {},
    onRecoveryGuide = {},
    onSendIssueReport = {},
)
