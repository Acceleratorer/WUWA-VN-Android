package com.acceleratorer.wuwavn

import android.content.Context

class InstalledStateDetector(
    private val stateReader: ShizukuInstalledStateReader,
    private val trustedBackupFinder: TrustedBackupFinder,
    private val configStateDetector: ConfigStateDetector,
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

        val pakExists = readResult.pakExists
        val mountLang = readResult.mountLang
        val mountLangExists = mountLang != null
        val mountLangPointsToPak = mountLangPointsToVietnamesePak(mountLang)
        val patchState = if (pakExists == null) {
            PatchInstallState.UNKNOWN
        } else {
            resolvePatchState(
                pakExists = pakExists,
                mountLangExists = mountLangExists,
                mountLangPointsToPak = mountLangPointsToPak,
            )
        }
        val configState = configStateDetector.detect(
            engineIni = readResult.engineIni,
            deviceProfilesIni = readResult.deviceProfilesIni,
            mountLang = mountLang,
        )

        diagnostics.add("PAK exists: ${pakExists ?: "unknown"}")
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
            mountLangExists: Boolean,
            mountLangPointsToPak: Boolean,
        ): PatchInstallState = when {
            pakExists && mountLangPointsToPak -> PatchInstallState.PATCHED
            !pakExists && mountLangExists && !mountLangPointsToPak -> PatchInstallState.ORIGINAL
            !pakExists && !mountLangExists -> PatchInstallState.PARTIAL
            pakExists && !mountLangPointsToPak -> PatchInstallState.PARTIAL
            !pakExists && mountLangPointsToPak -> PatchInstallState.PARTIAL
            else -> PatchInstallState.UNKNOWN
        }

        fun mountLangPointsToVietnamesePak(content: String?): Boolean {
            if (content.isNullOrBlank()) {
                return false
            }
            return content
                .replace('\\', '/')
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }
                .any { line -> line.contains("WuWaVH_99_P.pak", ignoreCase = true) }
        }
    }
}
