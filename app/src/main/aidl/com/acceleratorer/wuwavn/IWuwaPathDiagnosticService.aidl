package com.acceleratorer.wuwavn;

interface IWuwaPathDiagnosticService {
    boolean exists(String absolutePath);
    boolean isFile(String absolutePath);
    boolean isDirectory(String absolutePath);
    long length(String absolutePath);
    String[] listChildNames(String absolutePath, int maxEntries);
}
