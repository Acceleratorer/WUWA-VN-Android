package com.acceleratorer.wuwavn

data class PatchManifest(
    val patchVersion: String,
    val pakUrl: String,
    val pakSha256: String,
    val pakFileName: String,
)
