package com.acceleratorer.wuwavn;

interface IWuwaRestoreService {
    boolean exists(String absolutePath);
    long length(String absolutePath);
    byte[] readFile(String absolutePath, int maxBytes);
    void writeConfigFile(String absolutePath, in byte[] content, String expectedSha256);
}
