package com.acceleratorer.wuwavn

data class BackupFileInfo(
    val displayName: String,
    val relativePath: String,
    val sha256: String,
    val sizeBytes: Long,
)
