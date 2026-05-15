package com.acceleratorer.wuwavn;

final class BackupFileInfo {
    final String displayName;
    final String relativePath;
    final String sha256;
    final long sizeBytes;

    BackupFileInfo(String displayName, String relativePath, String sha256, long sizeBytes) {
        this.displayName = displayName;
        this.relativePath = relativePath;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
    }
}
