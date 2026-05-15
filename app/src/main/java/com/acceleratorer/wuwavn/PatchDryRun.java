package com.acceleratorer.wuwavn;

import java.io.File;
import java.util.List;

final class PatchDryRun {
    final List<String> filesToAdd;
    final List<String> filesToModify;
    final List<String> metadataFiles;
    final File backupDirectory;

    PatchDryRun(List<String> filesToAdd, List<String> filesToModify, List<String> metadataFiles, File backupDirectory) {
        this.filesToAdd = filesToAdd;
        this.filesToModify = filesToModify;
        this.metadataFiles = metadataFiles;
        this.backupDirectory = backupDirectory;
    }

    String describe() {
        StringBuilder builder = new StringBuilder();
        appendList(builder, "Files to add:", filesToAdd);
        builder.append('\n');
        appendList(builder, "Files to modify:", filesToModify);
        builder.append("\nBackup target:\n")
                .append(backupDirectory.getAbsolutePath())
                .append("\n\nApply Patch remains locked until backup/restore and Shizuku file writing are tested on a real device.");
        return builder.toString();
    }

    private static void appendList(StringBuilder builder, String title, List<String> values) {
        builder.append(title).append('\n');
        for (String value : values) {
            builder.append("- ").append(value).append('\n');
        }
    }
}
