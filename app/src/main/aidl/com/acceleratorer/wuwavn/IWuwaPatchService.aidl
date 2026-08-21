package com.acceleratorer.wuwavn;

interface IWuwaPatchService {
    boolean exists(String absolutePath);
    long length(String absolutePath);
    String sha256(String absolutePath);
    String wuwa36Snapshot(String preferredSeries);
    void beginWuWa36Install(
        String resourceVersion,
        String langVersion,
        String sigSourceRelativePath,
        long expectedPakSize,
        String expectedPakSha256,
        String expectedSigSha1,
        in byte[] mountLangContent,
        String expectedMountLangSha256
    );
    void writeWuWa36InstallChunk(in byte[] chunk, int length, long expectedSize);
    void finishWuWa36Install();
    boolean removeWuWa36Patch(
        String resourceVersion,
        String langVersion,
        in byte[] originalMountLangContent,
        String expectedMountLangSha256
    );
}
