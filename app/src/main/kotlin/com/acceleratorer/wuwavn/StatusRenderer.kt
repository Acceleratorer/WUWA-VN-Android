package com.acceleratorer.wuwavn

class StatusRenderer(
    private val manifestRepository: PatchManifestRepository,
    private val shizukuFileSystem: ShizukuFileSystem,
) {
    fun render(
        gameState: GamePackageDetector.State,
        gameInfo: GamePackageDetector.GameInfo?,
        shizukuState: ShizukuState,
        installedState: InstalledState?,
    ): String {
        val manifest = manifestRepository.current()
        val stateText = if (installedState == null) {
            "\nState detection: unavailable\n"
        } else {
            "\nPatch state: ${installedState.patchState}\n" +
                "Config state: ${configStateLabel(installedState.configState)}\n" +
                "Trusted backup: ${if (installedState.hasTrustedBackup) "found" else "missing"}\n" +
                "PAK: ${if (installedState.pakExists) "exists" else "not found"}\n" +
                "MountLang_en.txt: ${mountLangLabel(installedState)}\n" +
                "Engine.ini: ${if (installedState.engineIniReadable) "readable" else "not readable"}\n" +
                "DeviceProfiles.ini: ${if (installedState.deviceProfilesReadable) "readable" else "not readable"}\n" +
                "Recommended action: ${recommendedAction(installedState)}\n" +
                recoveryGuide(installedState)
        }

        return "Status\n" +
            "Game: ${gameState.label}\n" +
            "Game package: ${gameInfo?.packageName ?: AppConstants.GLOBAL_GAME_PACKAGE}\n" +
            "Game version: ${gameInfo?.versionName ?: "unknown"}\n" +
            "Launcher compatibility: WUWA Global ${AppConstants.SUPPORTED_GAME_VERSION}\n" +
            "Compatibility status: ${gameInfo?.compatibilityLabel ?: "game package not detected"}\n" +
            "Shizuku: ${shizukuState.label}\n" +
            "Download & Verify Patch: available without Shizuku; install/remove requires Shizuku READY.\n" +
            stateText +
            "Patch: ${manifest.patchVersion}\n" +
            "Patch SHA-256: ${manifest.pakSha256.take(12)}...\n" +
            "Mode: Safe / Default + Balanced + Performance\n" +
            "Restore writing: ${if (shizukuFileSystem.isRestoreWriteEnabled(shizukuState)) "enabled" else "locked"}\n" +
            "Patch writing: ${shizukuFileSystem.patchWriteStatus(shizukuState)}\n" +
            "Config preset writing: ${shizukuFileSystem.configPresetWriteStatus(shizukuState)}"
    }

    private fun configStateLabel(configState: ConfigInstallState): String = when (configState) {
        ConfigInstallState.ORIGINAL -> "ORIGINAL"
        ConfigInstallState.SAFE_DEFAULT -> "SAFE / DEFAULT"
        ConfigInstallState.BALANCED -> "BALANCED"
        ConfigInstallState.PERFORMANCE -> "PERFORMANCE"
        ConfigInstallState.CUSTOM -> "CUSTOM"
        ConfigInstallState.UNKNOWN -> "UNKNOWN"
    }

    private fun mountLangLabel(installedState: InstalledState): String = when {
        !installedState.mountLangExists -> "missing"
        installedState.mountLangPointsToPak -> "points to WuWaVH_99_P.pak"
        else -> "does not point to WuWaVH_99_P.pak"
    }

    private fun recommendedAction(installedState: InstalledState): String = when (installedState.patchState) {
        PatchInstallState.ORIGINAL ->
            "Backup Game Configs -> Download & Verify Patch -> Install Vietnamese Patch."
        PatchInstallState.PATCHED ->
            "Remove Vietnamese Patch or Restore Original Files is available."
        PatchInstallState.PARTIAL ->
            "Use Remove Vietnamese Patch or Restore Original Files."
        PatchInstallState.UNKNOWN ->
            "Complete game/Shizuku setup or restore from a trusted backup."
    }

    private fun recoveryGuide(installedState: InstalledState): String = when (installedState.patchState) {
        PatchInstallState.PARTIAL ->
            "\nPartial state detected.\n" +
                "Recommended recovery:\n" +
                "1. Use Remove Vietnamese Patch if PAK exists.\n" +
                "2. Use Restore Original Files if config files look wrong.\n" +
                "3. Do not apply Balanced or Performance until state becomes PATCHED.\n"
        PatchInstallState.UNKNOWN ->
            "\nState unknown.\n" +
                "Recommended:\n" +
                "1. Open Shizuku.\n" +
                "2. Grant permission.\n" +
                "3. Check Game Folder.\n" +
                "4. Restore from trusted backup if needed.\n"
        else -> ""
    }
}
