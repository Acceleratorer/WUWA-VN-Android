package com.acceleratorer.wuwavn

import java.io.File

data class RestoreDryRun(
    val sessionDirectory: File,
    val createdAt: String,
    val backupType: String,
    val files: List<RestoreFilePlan>,
) {
    fun verifiedCount(): Int = files.count { it.status == RestoreFileStatus.VERIFIED }
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
