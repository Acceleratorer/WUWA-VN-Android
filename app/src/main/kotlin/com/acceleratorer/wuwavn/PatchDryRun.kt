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
        append("\n\nUnlocked write flows are separate and verified: PAK-only install, Safe / Default config preset, and Remove Patch with MountLang rollback. Balanced is preview-only; Balanced write, Performance, and Max Graphics remain locked.")
    }

    private fun StringBuilder.appendList(title: String, values: List<String>) {
        append(title).append('\n')
        for (value in values) {
            append("- ").append(value).append('\n')
        }
    }
}
