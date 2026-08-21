package com.acceleratorer.wuwavn

import android.content.Context

class InstalledStateDetector(
    private val stateReader: ShizukuInstalledStateReader,
    private val trustedBackupFinder: TrustedBackupFinder,
) {
    fun detect(
        context: Context,
        gameInfo: GamePackageDetector.GameInfo?,
        gameState: GamePackageDetector.State,
        shizukuState: ShizukuState,
    ): InstalledState {
        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            return baseUnknown(
                gameInfo = gameInfo,
                trustedBackup = null,
                diagnostics = listOf("Wuthering Waves Global is not detected."),
            )
        }

        val trustedBackup = trustedBackupFinder.find(context)
        if (shizukuState != ShizukuState.READY) {
            return baseUnknown(
                gameInfo = gameInfo,
                trustedBackup = trustedBackup,
                diagnostics = listOf("Shizuku is not READY."),
            )
        }

        val readResult = stateReader.read(context)
        val diagnostics = mutableListOf<String>()
        diagnostics.addAll(readResult.diagnostics)

        val snapshot = readResult.snapshot
        val pakExists = snapshot?.pakExists
        val mountLang = snapshot?.mountLangContent
        val mountLangExists = mountLang != null
        val mountLangPointsToPak = snapshot?.patchRegistered == true
        val patchState = if (snapshot == null) {
            PatchInstallState.UNKNOWN
        } else {
            resolvePatchState(
                pakExists = snapshot.pakExists,
                sigExists = snapshot.sigExists,
                mountLangExists = mountLangExists,
                mountLangPointsToPak = mountLangPointsToPak,
                mountLangValid = mountLang?.let(WuWa36Layout::isValidMountLang) == true,
            )
        }
        val configState = ConfigStateDetector().detect(
            readResult.engineIni,
            readResult.deviceProfilesIni,
        )

        diagnostics.add("WUWA 3.6 resource version: ${snapshot?.resolvedResourceVersion ?: "unknown"}")
        diagnostics.add("WUWA 3.6 language version: ${snapshot?.resolvedLanguageVersion ?: "unknown"}")
        diagnostics.add("PAK exists: ${pakExists ?: "unknown"}")
        diagnostics.add("SIG exists: ${snapshot?.sigExists ?: "unknown"}")
        diagnostics.add("MountLang exists: $mountLangExists")
        diagnostics.add("MountLang points to PAK: $mountLangPointsToPak")
        diagnostics.add("Engine.ini readable: ${readResult.engineIni != null}")
        diagnostics.add("DeviceProfiles.ini readable: ${readResult.deviceProfilesIni != null}")
        diagnostics.add("Config state: $configState")
        diagnostics.add("Trusted backup: ${trustedBackup != null}")

        return InstalledState(
            patchState = patchState,
            configState = configState,
            pakExists = pakExists == true,
            mountLangExists = mountLangExists,
            mountLangPointsToPak = mountLangPointsToPak,
            engineIniReadable = readResult.engineIni != null,
            deviceProfilesReadable = readResult.deviceProfilesIni != null,
            hasTrustedBackup = trustedBackup != null,
            trustedBackupPath = trustedBackup?.sessionDirectory?.absolutePath,
            gameVersionName = gameInfo?.versionName,
            supportedGameVersion = AppConstants.SUPPORTED_GAME_VERSION,
            diagnostics = diagnostics,
        )
    }

    private fun baseUnknown(
        gameInfo: GamePackageDetector.GameInfo?,
        trustedBackup: RestoreDryRun?,
        diagnostics: List<String>,
    ): InstalledState = InstalledState(
        patchState = PatchInstallState.UNKNOWN,
        configState = ConfigInstallState.UNKNOWN,
        pakExists = false,
        mountLangExists = false,
        mountLangPointsToPak = false,
        engineIniReadable = false,
        deviceProfilesReadable = false,
        hasTrustedBackup = trustedBackup != null,
        trustedBackupPath = trustedBackup?.sessionDirectory?.absolutePath,
        gameVersionName = gameInfo?.versionName,
        supportedGameVersion = AppConstants.SUPPORTED_GAME_VERSION,
        diagnostics = diagnostics,
    )

    companion object {
        fun resolvePatchState(
            pakExists: Boolean,
            sigExists: Boolean,
            mountLangExists: Boolean,
            mountLangPointsToPak: Boolean,
            mountLangValid: Boolean = true,
        ): PatchInstallState = when {
            pakExists && sigExists && mountLangPointsToPak -> PatchInstallState.PATCHED
            !pakExists && !sigExists && mountLangExists && mountLangValid && !mountLangPointsToPak -> PatchInstallState.ORIGINAL
            !pakExists && !sigExists && !mountLangExists -> PatchInstallState.PARTIAL
            else -> PatchInstallState.UNKNOWN
        }
    }
}
