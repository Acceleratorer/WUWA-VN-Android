package com.acceleratorer.wuwavn

enum class PatchInstallState {
    ORIGINAL,
    PATCHED,
    PARTIAL,
    UNKNOWN,
}

enum class ConfigInstallState {
    // Reserved for future original-config fingerprint detection from trusted backup metadata.
    ORIGINAL,
    SAFE_DEFAULT,
    BALANCED,
    CUSTOM,
    UNKNOWN,
}

data class InstalledState(
    val patchState: PatchInstallState,
    val configState: ConfigInstallState,
    val pakExists: Boolean,
    val mountLangExists: Boolean,
    val mountLangPointsToPak: Boolean,
    val engineIniReadable: Boolean,
    val deviceProfilesReadable: Boolean,
    val hasTrustedBackup: Boolean,
    val trustedBackupPath: String?,
    val gameVersionName: String?,
    val supportedGameVersion: String,
    val diagnostics: List<String>,
)
