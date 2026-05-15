package com.acceleratorer.wuwavn;

final class PatchManifestRepository {
    PatchManifest current() {
        return new PatchManifest(
                "2026.05.15",
                "https://github.com/CallMeDangDev/WuwaVH/releases/latest/download/WuWaVH_99_P.pak",
                "99202f84db73e771cd68ef1734893ee5f589666654fe7be3dcfff631df199a78"
        );
    }
}
