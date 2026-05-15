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
        append("\n\nApply Patch remains locked until backup/restore and Shizuku file writing are tested on a real device.")
    }

    private fun StringBuilder.appendList(title: String, values: List<String>) {
        append(title).append('\n')
        for (value in values) {
            append("- ").append(value).append('\n')
        }
    }
}
