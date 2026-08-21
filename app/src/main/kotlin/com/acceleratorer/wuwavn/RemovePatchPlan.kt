package com.acceleratorer.wuwavn

data class RemovePatchPrecondition(
    val plan: RemovePatchPlan?,
    val failures: List<String>,
) {
    fun isReady(): Boolean = plan != null && failures.isEmpty()
}

data class RemovePatchPlan(
    val targetRelativePath: String,
    val targetDisplayName: String,
    val resourceVersion: String,
    val langVersion: String,
    val mountLangContent: ByteArray,
    val mountLangSha256: String,
    val trustedBackupDryRun: RestoreDryRun,
    val mountLangFile: RestoreFilePlan,
)
