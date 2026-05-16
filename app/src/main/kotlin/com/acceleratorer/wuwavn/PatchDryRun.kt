package com.acceleratorer.wuwavn

import java.io.File

data class PatchDryRun(
    val filesToAdd: List<String>,
    val filesToModify: List<String>,
    val metadataFiles: List<String>,
    val backupDirectory: File,
) {
    fun describe(): String = buildString {
        appendList("Files to add:", filesToAdd)
        append('\n')
        appendList("Files to modify:", filesToModify)
        append("\nBackup target:\n")
        append(backupDirectory.absolutePath)
        append("\n\nv2.6.0 can install only WuWaVH_99_P.pak. Config file modification remains locked for a later release.")
    }

    private fun StringBuilder.appendList(title: String, values: List<String>) {
        append(title).append('\n')
        for (value in values) {
            append("- ").append(value).append('\n')
        }
    }
}
