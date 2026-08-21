package com.acceleratorer.wuwavn

import java.io.File

data class PatchWritePrecondition(
    val plan: PatchWritePlan?,
    val failures: List<String>,
) {
    fun isReady(): Boolean = plan != null && failures.isEmpty()
}

data class PatchWritePlan(
    val manifest: PatchManifest,
    val patchFile: File,
    val patchSizeBytes: Long,
    val patchSha256: String,
    val targetRelativePath: String,
    val targetDisplayName: String,
    val trustedBackup: TrustedBackupInfo,
    val resourceVersion: String,
    val langVersion: String,
    val sigSourceRelativePath: String,
    val sigSha1: String,
    val mountLangContent: ByteArray,
    val mountLangSha256: String,
)

data class TrustedBackupInfo(
    val sessionDirectory: File,
    val createdAt: String,
    val verifiedFiles: Int,
)
