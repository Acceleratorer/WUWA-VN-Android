package com.acceleratorer.wuwavn;

enum ShizukuState {
    NOT_INSTALLED("Shizuku not installed"),
    INSTALLED_NOT_RUNNING("Shizuku installed but not running"),
    RUNNING_PERMISSION_DENIED("Shizuku running but permission not granted"),
    READY("Ready");

    private final String label;

    ShizukuState(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }
}
