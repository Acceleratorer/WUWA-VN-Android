package com.acceleratorer.wuwavn

import android.content.Context

class PatchDryRunPlanner(
    private val backupManager: BackupManager,
) {
    fun plan(context: Context): PatchDryRun {
        assertAllowed(PATCH_PAK)
        for (relativePath in BACKUP_RELATIVE_PATHS) {
            assertAllowed(relativePath)
        }

        val filesToAdd = listOf("WuWaVH_99_P.pak")
        val filesToModify = listOf("Engine.ini", "DeviceProfiles.ini", "MountLang_en.txt")
        val metadataFiles = listOf(
            "Engine.ini",
            "DeviceProfiles.ini",
            "MountLang_en.txt",
            "WuWaVH_99_P.pak",
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
        private const val MOUNT_LANG = "UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt"
        private const val PATCH_PAK = "UE4Game/Client/Client/Content/Paks/WuWaVH_99_P.pak"

        private val BACKUP_RELATIVE_PATHS = listOf(
            ENGINE_INI,
            DEVICE_PROFILES_INI,
            MOUNT_LANG,
        )

        private val ALLOWED_TARGETS = setOf(
            ENGINE_INI,
            DEVICE_PROFILES_INI,
            MOUNT_LANG,
            PATCH_PAK,
        )

        fun isAllowedTarget(relativePath: String): Boolean {
            val normalized = relativePath.replace('\\', '/')
            return !normalized.contains("..") && ALLOWED_TARGETS.contains(normalized)
        }

        fun backupRelativePaths(): List<String> = BACKUP_RELATIVE_PATHS

        fun engineIniRelativePath(): String = ENGINE_INI

        fun deviceProfilesRelativePath(): String = DEVICE_PROFILES_INI

        fun mountLangRelativePath(): String = MOUNT_LANG

        fun patchPakRelativePath(): String = PATCH_PAK

        fun displayName(relativePath: String): String {
            val normalized = relativePath.replace('\\', '/')
            val slash = normalized.lastIndexOf('/')
            return if (slash >= 0) normalized.substring(slash + 1) else normalized
        }

        private fun assertAllowed(relativePath: String) {
            if (!isAllowedTarget(relativePath)) {
                throw SecurityException("Blocked unsafe target path: $relativePath")
            }
        }
    }
}
