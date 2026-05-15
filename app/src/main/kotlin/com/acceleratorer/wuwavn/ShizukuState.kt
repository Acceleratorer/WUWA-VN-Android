package com.acceleratorer.wuwavn

enum class ShizukuState(val label: String) {
    NOT_INSTALLED("Shizuku not installed"),
    INSTALLED_NOT_RUNNING("Shizuku installed but not running"),
    RUNNING_PERMISSION_DENIED("Shizuku running but permission not granted"),
    READY("Ready"),
}
