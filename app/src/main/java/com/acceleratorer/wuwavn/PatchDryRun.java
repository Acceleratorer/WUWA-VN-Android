package com.acceleratorer.wuwavn;

import java.io.File;
import java.util.List;

final class PatchDryRun {
    final List<String> filesToAdd;
    final List<String> filesToModify;
    final File backupDirectory;

    PatchDryRun(List<String> filesToAdd, List<String> filesToModify, File backupDirectory) {
        this.filesToAdd = filesToAdd;
        this.filesToModify = filesToModify;
        this.backupDirectory = backupDirectory;
    }

    String describe() {
        return "Files to add:\n" +
                "- " + filesToAdd.get(0) + "\n\n" +
                "Files to modify:\n" +
                "- " + filesToModify.get(0) + "\n" +
                "- " + filesToModify.get(1) + "\n" +
                "- " + filesToModify.get(2) + "\n\n" +
                "Backup target:\n" +
                backupDirectory.getAbsolutePath() + "\n\n" +
                "Apply Patch remains locked until backup/restore and Shizuku file writing are tested on a real device.";
    }
}
