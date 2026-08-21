package com.acceleratorer.wuwavn

data class GamePathDiagnosticReport(
    val snapshot: WuWa36Snapshot?,
    val verifiedLocalPak: Boolean,
    val localPakSizeBytes: Long?,
    val localPakSha256: String?,
    val error: String? = null,
    val source: String = "Shizuku WUWA 3.6 snapshot service",
)

object GamePathDiagnosticPaths {
    val allowedAbsoluteRelativePaths: Set<String> = emptySet()
}

object GamePathDiagnosticRenderer {
    fun render(
        report: GamePathDiagnosticReport,
        gameInfo: GamePackageDetector.GameInfo?,
        shizukuState: ShizukuState,
    ): String = buildString {
        appendLine("WUWA VN Game Path Diagnostic")
        appendLine("App version: ${AppConstants.VERSION_NAME} (${AppConstants.VERSION_CODE})")
        appendLine("Game package: ${gameInfo?.packageName ?: AppConstants.GLOBAL_GAME_PACKAGE}")
        appendLine("Game version: ${gameInfo?.versionName ?: "unknown"}")
        appendLine("Supported series: ${AppConstants.SUPPORTED_GAME_VERSION}")
        appendLine("Shizuku: ${shizukuState.label}")
        appendLine("Mode: read-only diagnostic, no files changed")
        appendLine("Source: ${report.source}")
        appendLine()

        val snapshot = report.snapshot
        if (report.error != null || snapshot == null) {
            appendLine("WUWA 3.6 Resources layout: NOT READY")
            appendLine("Error: ${report.error ?: "No 3.6 resource version with ResManifest was resolved."}")
            appendLine()
            appendLine("Check that Wuthering Waves Global 3.6 has completed its in-game resource download, then retry.")
            return@buildString
        }

        appendLine("WUWA 3.6 Resources layout: ${if (snapshot.isReadyForPatch) "READY" else "INCOMPLETE"}")
        appendLine("Resource versions: ${snapshot.resourceVersions.joinToString().ifEmpty { "none" }}")
        appendLine("Versions with ResManifest: ${snapshot.versionsWithManifest.joinToString().ifEmpty { "none" }}")
        appendLine("Resolved resource version: ${snapshot.resolvedResourceVersion}")
        appendLine("Resolved Lang_en version: ${snapshot.resolvedLanguageVersion}")
        appendLine()

        appendLine("Mount registry:")
        appendLine("- Target: ${snapshot.mountLangRelativePath}")
        appendLine("- Exists and valid: ${snapshot.mountLangContent?.let(WuWa36Layout::isValidMountLang) == true}")
        appendLine("- Vietnamese patch registered: ${snapshot.patchRegistered}")
        appendLine()

        appendLine("Patch pair:")
        appendLine("- PAK: ${snapshot.pakRelativePath}")
        appendLine("  Exists: ${snapshot.pakExists}")
        appendLine("- SIG: ${snapshot.sigRelativePath}")
        appendLine("  Exists: ${snapshot.sigExists}")
        appendLine()

        appendLine("Official SIG source:")
        appendLine("- Selected: ${snapshot.sigSourceRelativePath ?: "not found"}")
        appendLine("- SHA-1: ${snapshot.sigSourceSha1 ?: "not available"}")
        appendLine("- Candidates: ${snapshot.sigSourceRelativePaths.size}")
        appendLine()

        appendLine("Verified local patch payload:")
        appendLine("- Available: ${report.verifiedLocalPak}")
        appendLine("- Size: ${report.localPakSizeBytes?.let { "$it bytes" } ?: "not available"}")
        appendLine("- SHA-256: ${report.localPakSha256 ?: "not available"}")
        appendLine()

        appendLine("Notes:")
        appendLine("- Install/update writes only the resolved 3.6 PAK, paired SIG, and MountLang registry.")
        appendLine("- Remove restores the verified Resources MountLang backup and removes the PAK/SIG pair transactionally.")
        appendLine("- Config presets and root writes remain locked for this 3.6 patch line.")
    }
}
