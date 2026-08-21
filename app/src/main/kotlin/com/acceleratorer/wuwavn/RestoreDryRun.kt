package com.acceleratorer.wuwavn

import java.io.File

data class RestoreDryRun(
    val sessionDirectory: File,
    val createdAt: String,
    val gamePackage: String,
    val backupType: String,
    val restoreWriteEnabled: Boolean?,
    val files: List<RestoreFilePlan>,
) {
    fun verifiedCount(): Int = files.count { it.status == RestoreFileStatus.VERIFIED }

    fun allFilesVerified(): Boolean =
        files.isNotEmpty() && files.all { it.status == RestoreFileStatus.VERIFIED }

    fun hasVerifiedRequiredConfigFiles(): Boolean {
        val verifiedPaths = files
            .filter { it.status == RestoreFileStatus.VERIFIED }
            .map { it.relativePath }
            .toSet()
        return requiredBackupPathsSatisfied(verifiedPaths)
    }

    fun hasOnlyVerifiedRequiredConfigFiles(): Boolean {
        val filePaths = files.map { it.relativePath }
        return requiredBackupPathsSatisfied(filePaths.toSet()) &&
            filePaths.size == filePaths.toSet().size &&
            filePaths.size == 3 &&
            files.all { it.status == RestoreFileStatus.VERIFIED }
    }

    private fun requiredBackupPathsSatisfied(paths: Set<String>): Boolean {
        val engine = PatchDryRunPlanner.engineIniRelativePath()
        val device = PatchDryRunPlanner.deviceProfilesRelativePath()
        val mountCount = paths.count(WuWa36Layout::isMountLangPath)
        return engine in paths && device in paths && mountCount == 1
    }
}

data class RestoreFilePlan(
    val displayName: String,
    val relativePath: String,
    val expectedSha256: String?,
    val actualSha256: String?,
    val sizeBytes: Long,
    val status: RestoreFileStatus,
)

enum class RestoreFileStatus(val label: String) {
    VERIFIED("verified"),
    MISSING("missing"),
    HASH_MISMATCH("hash mismatch"),
    HASH_NOT_AVAILABLE("hash not available"),
    NOT_BACKED_UP("not backed up"),
    UNSAFE_METADATA("blocked unsafe metadata"),
    READ_FAILED("read failed"),
}
