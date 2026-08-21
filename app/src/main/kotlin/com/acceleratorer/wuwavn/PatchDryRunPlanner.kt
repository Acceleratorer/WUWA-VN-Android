package com.acceleratorer.wuwavn

import android.content.Context

class PatchDryRunPlanner(
    private val backupManager: BackupManager,
) {
    fun plan(context: Context): PatchDryRun {
        val filesToAdd = listOf("WuWaVH_99_P.pak", "WuWaVH_99_P.sig")
        val filesToModify = listOf("Resources/<3.6.x>/Mount/MountLang_en.txt")
        val metadataFiles = listOf(
            "WuWaVH_99_P.pak",
            "WuWaVH_99_P.sig",
            "Resources/<3.6.x>/Mount/MountLang_en.txt",
        )
        return PatchDryRun(
            filesToAdd = filesToAdd,
            filesToModify = filesToModify,
            metadataFiles = metadataFiles,
            backupDirectory = backupManager.planBackupSession(context),
        )
    }

    companion object {
        private const val ENGINE_INI = "UE4Game/Client/Client/Saved/Config/Android/Engine.ini"
        private const val DEVICE_PROFILES_INI = "UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini"
        private val CONFIG_RELATIVE_PATHS = listOf(
            ENGINE_INI,
            DEVICE_PROFILES_INI,
        )

        private val READ_ONLY_BACKUP_RELATIVE_PATHS = CONFIG_RELATIVE_PATHS

        private val ALLOWED_TARGETS = setOf(
            ENGINE_INI,
            DEVICE_PROFILES_INI,
        )

        fun isAllowedTarget(relativePath: String): Boolean {
            val normalized = relativePath.replace('\\', '/')
            return !normalized.contains("..") && ALLOWED_TARGETS.contains(normalized)
        }

        fun backupRelativePaths(): List<String> = CONFIG_RELATIVE_PATHS

        fun backupReadableRelativePaths(dynamicMountLangPath: String? = null): List<String> =
            if (dynamicMountLangPath != null) {
                listOf(ENGINE_INI, DEVICE_PROFILES_INI, dynamicMountLangPath)
            } else {
                READ_ONLY_BACKUP_RELATIVE_PATHS
            }

        fun isAllowedBackupReadTarget(relativePath: String): Boolean {
            val normalized = relativePath.replace('\\', '/')
            return !normalized.contains("..") &&
                (READ_ONLY_BACKUP_RELATIVE_PATHS.contains(normalized) || WuWa36Layout.isMountLangPath(normalized))
        }

        fun engineIniRelativePath(): String = ENGINE_INI

        fun deviceProfilesRelativePath(): String = DEVICE_PROFILES_INI


        fun displayName(relativePath: String): String {
            val normalized = relativePath.replace('\\', '/')
            val slash = normalized.lastIndexOf('/')
            return if (slash >= 0) normalized.substring(slash + 1) else normalized
        }

        fun backupDisplayName(relativePath: String): String =
            if (WuWa36Layout.isMountLangPath(relativePath)) {
                "MountLang_en.Resources-${relativePath.substringAfter("Saved/Resources/").substringBefore('/')}.txt"
            } else {
                displayName(relativePath)
            }

    }
}
