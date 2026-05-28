package com.acceleratorer.wuwavn;

interface IWuwaBackupService {
    boolean exists(String absolutePath);
    long length(String absolutePath);
    byte[] readFile(String absolutePath, int maxBytes);
    boolean pathExists(String absolutePath);
    boolean isFile(String absolutePath);
    boolean isDirectory(String absolutePath);
    String[] listChildNames(String absolutePath, int maxEntries);
    String sha1(String absolutePath);
}
