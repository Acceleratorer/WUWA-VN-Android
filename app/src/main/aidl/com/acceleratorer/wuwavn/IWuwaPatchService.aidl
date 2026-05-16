package com.acceleratorer.wuwavn;

interface IWuwaPatchService {
    boolean exists(String absolutePath);
    long length(String absolutePath);
    String sha256(String absolutePath);
    void beginWritePatch(String absolutePath, long expectedSize, String expectedSha256);
    void writePatchChunk(String absolutePath, in byte[] chunk, int length, long expectedSize);
    void finishWritePatch(String absolutePath, long expectedSize, String expectedSha256);
}
