package com.acceleratorer.wuwavn

class StatusRenderer(
    private val manifestRepository: PatchManifestRepository,
    private val shizukuFileSystem: ShizukuFileSystem,
) {
    fun render(
        gameState: GamePackageDetector.State,
        gameInfo: GamePackageDetector.GameInfo?,
        shizukuState: ShizukuState,
    ): String {
        val manifest = manifestRepository.current()
        return "Status\n" +
            "Game: ${gameState.label}\n" +
            "Game package: ${gameInfo?.packageName ?: AppConstants.GLOBAL_GAME_PACKAGE}\n" +
            "Game version: ${gameInfo?.versionName ?: "unknown"}\n" +
            "Launcher compatibility: WUWA Global ${AppConstants.SUPPORTED_GAME_VERSION}\n" +
            "Compatibility status: ${gameInfo?.compatibilityLabel ?: "game package not detected"}\n" +
            "Shizuku: ${shizukuState.label}\n" +
            "Patch: ${manifest.patchVersion}\n" +
            "Patch SHA-256: ${manifest.pakSha256.take(12)}...\n" +
            "Mode: Safe / Default\n" +
            "Restore writing: ${if (shizukuFileSystem.isRestoreWriteEnabled(shizukuState)) "enabled" else "locked"}\n" +
            "Patch writing: ${shizukuFileSystem.patchWriteStatus(shizukuState)}\n" +
            "Config preset writing: ${shizukuFileSystem.configPresetWriteStatus(shizukuState)}"
    }
}
