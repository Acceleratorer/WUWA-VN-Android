package com.acceleratorer.wuwavn;

final class PatchManifest {
    final String patchVersion;
    final String pakUrl;
    final String pakSha256;

    PatchManifest(String patchVersion, String pakUrl, String pakSha256) {
        this.patchVersion = patchVersion;
        this.pakUrl = pakUrl;
        this.pakSha256 = pakSha256;
    }
}
