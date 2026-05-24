package com.acceleratorer.wuwavn

object RootPreviewRenderer {
    fun render(state: RootAccessState): String = buildString {
        appendLine("Optional Root Backend Preview")
        appendLine("Status: ${state.label}")
        appendLine("Root write enabled: false")
        appendLine("Default backend: Shizuku")
        appendLine()
        appendLine("Use this only if your device is already rooted and Shizuku is hard to set up.")
        appendLine("This version only checks whether root is available. It does not backup, install, remove, restore, or apply presets through root.")
        appendLine("If a root manager popup appears, you can deny it and keep using Shizuku.")
    }

    fun help(state: RootAccessState): String =
        "Optional Root Backend Preview\n\n" +
            "Current status: ${state.label}\n\n" +
            "For normal users, Shizuku is still recommended.\n\n" +
            "Root is only for phones or emulators that are already rooted. This preview can check root access, but it cannot write game files with root yet.\n\n" +
            "No root write actions are enabled in this version.\n\n" +
            "Safety rules stay the same:\n" +
            "1. Only allowlisted WUWA files may ever be touched.\n" +
            "2. SHA-256 verification stays required.\n" +
            "3. Max Graphics remains locked.\n" +
            "4. FPS, Vulkan, resolution, and high-risk graphics overrides remain blocked."

    fun result(state: RootAccessState): String = when (state) {
        RootAccessState.AVAILABLE ->
            "Root access was detected.\n\nThis is preview-only. Root writes are still disabled, so keep using the normal Shizuku buttons for backup, install, remove, restore, and presets."
        RootAccessState.NOT_AVAILABLE ->
            "Root was not detected.\n\nThat is OK. Use Shizuku, which remains the recommended setup for normal users."
        RootAccessState.DENIED ->
            "Root permission was denied or timed out.\n\nThat is OK. You can ignore root preview and keep using Shizuku."
        RootAccessState.CHECK_FAILED ->
            "Root check failed.\n\nNo files were changed. Use Shizuku and send an issue report if you want help."
        RootAccessState.NOT_CHECKED ->
            help(state)
        RootAccessState.CHECKING ->
            "Root check is running. If a root manager popup appears, allow only if you understand it. Denying is safe."
    }
}
