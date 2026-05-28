package com.acceleratorer.wuwavn

class PatchManifestRepository {
    fun current(): PatchManifest = PatchManifest(
        patchVersion = "wuwa-3.3.6-vi-2026.05",
        pakUrl = "https://github.com/CallMeDangDev/WuwaVH/releases/download/3.3.6/WuWaVH_99_P.pak",
        pakSha256 = "146abe4d4d0d17036776ac82ba26c0b4c6b1a4f073b8c84fed91a327d013eb85",
        pakFileName = "WuWaVH_99_P.pak",
    )
}
