package com.acceleratorer.wuwavn

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ComposePreviewHomeState(
    val patchState: String,
    val configState: String,
    val shizukuState: String,
    val trustedBackup: Boolean,
    val primaryHint: String,
    val actions: List<ComposePreviewAction>,
)

private data class ComposePreviewAction(
    val label: String,
    val enabled: Boolean,
)

@Composable
private fun WuwaPreviewTheme(content: @Composable () -> Unit) {
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
private fun WuwaHomePreviewScreen(state: ComposePreviewHomeState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeaderBlock()
            SetupChecklistBlock(state)
            ActionSection("Setup", state.actions.take(4))
            ActionSection("Patch", state.actions.drop(4).take(5))
            ActionSection("Config Presets", state.actions.drop(9).take(3))
            ActionSection("Diagnostics", state.actions.drop(12))
            PolicyBlock()
        }
    }
}

@Composable
private fun HeaderBlock() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "WUWA VN",
            color = Color(0xFF172033),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Safe patching workflow for Wuthering Waves Global 3.3",
            color = Color(0xFF516070),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SetupChecklistBlock(state: ComposePreviewHomeState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Setup Checklist", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            StatusRow("Patch state", state.patchState)
            StatusRow("Config state", state.configState)
            StatusRow("Shizuku", state.shizukuState)
            StatusRow("Trusted backup", if (state.trustedBackup) "OK" else "Missing")
            Text(state.primaryHint, color = Color(0xFF475569), fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFF64748B), fontSize = 13.sp)
        Text(value, color = Color(0xFF172033), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActionSection(title: String, actions: List<ComposePreviewAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFF2563EB), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEach { action ->
                if (action.enabled) {
                    Button(
                        onClick = {},
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color(0xFF2563EB),
                            disabledContentColor = Color.White,
                        ),
                    ) {
                        Text(action.label)
                    }
                } else {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyBlock() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Preset Write Policy", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            StatusRow("Safe", ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.SAFE_DEFAULT).name)
            StatusRow("Balanced", ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.BALANCED).name)
            StatusRow("Performance", ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.PERFORMANCE).name)
            StatusRow("Max Graphics", ConfigPresetAvailabilityPolicy.availability(ConfigPresetId.MAX_GRAPHICS).name)
        }
    }
}

@Preview(name = "WUWA Home - Patched", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WuwaPatchedPreview() {
    WuwaPreviewTheme {
        WuwaHomePreviewScreen(
            state = ComposePreviewHomeState(
                patchState = "PATCHED",
                configState = "PERFORMANCE",
                shizukuState = "READY",
                trustedBackup = true,
                primaryHint = "Safe, Balanced, Performance, Remove, or Restore is available.",
                actions = previewActions(
                    install = false,
                    safe = true,
                    balanced = true,
                    performance = true,
                    remove = true,
                    restore = true,
                    backup = true,
                ),
            ),
        )
    }
}

@Preview(name = "WUWA Home - Setup Blocked", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WuwaSetupBlockedPreview() {
    WuwaPreviewTheme {
        WuwaHomePreviewScreen(
            state = ComposePreviewHomeState(
                patchState = "UNKNOWN",
                configState = "UNKNOWN",
                shizukuState = "Shizuku installed but not running",
                trustedBackup = false,
                primaryHint = "Complete game/Shizuku setup before file operations.",
                actions = previewActions(
                    install = false,
                    safe = false,
                    balanced = false,
                    performance = false,
                    remove = false,
                    restore = false,
                    backup = false,
                ),
            ),
        )
    }
}

@Preview(name = "WUWA Setup Guide", showBackground = true, widthDp = 390)
@Composable
private fun WuwaSetupGuidePreview() {
    WuwaPreviewTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeaderBlock()
                Spacer(modifier = Modifier.height(4.dp))
                listOf(
                    "Install Wuthering Waves Global.",
                    "Install and start Shizuku.",
                    "Grant Shizuku permission to WUWA VN.",
                    "Backup Game Configs.",
                    "Download and verify patch.",
                    "Install Vietnamese Patch.",
                    "Apply presets only when state is PATCHED.",
                ).forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("${index + 1}.", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(step, color = Color(0xFF172033), fontSize = 14.sp)
                    }
                }
                Text("Max Graphics remains locked.", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun previewActions(
    install: Boolean,
    safe: Boolean,
    balanced: Boolean,
    performance: Boolean,
    remove: Boolean,
    restore: Boolean,
    backup: Boolean,
): List<ComposePreviewAction> = listOf(
    ComposePreviewAction("Show Setup Guide", true),
    ComposePreviewAction("Shizuku Setup Help", true),
    ComposePreviewAction("Open Shizuku", true),
    ComposePreviewAction("Open Developer Options", true),
    ComposePreviewAction("Show Patch Plan", true),
    ComposePreviewAction("Backup Game Configs", backup),
    ComposePreviewAction("Download and Verify Patch", true),
    ComposePreviewAction("Install Vietnamese Patch", install),
    ComposePreviewAction("Remove Vietnamese Patch", remove),
    ComposePreviewAction("Apply Safe", safe),
    ComposePreviewAction("Apply Balanced", balanced),
    ComposePreviewAction("Apply Performance", performance),
    ComposePreviewAction("Copy State Snapshot", true),
    ComposePreviewAction("Recovery Guide", true),
    ComposePreviewAction("Restore Original", restore),
)
