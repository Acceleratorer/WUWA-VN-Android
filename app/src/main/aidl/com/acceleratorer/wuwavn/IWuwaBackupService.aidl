package com.acceleratorer.wuwavn;

interface IWuwaBackupService {
    boolean exists(String absolutePath);
    long length(String absolutePath);
    byte[] readFile(String absolutePath, int maxBytes);
}
