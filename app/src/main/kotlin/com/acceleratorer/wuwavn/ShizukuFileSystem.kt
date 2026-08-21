package com.acceleratorer.wuwavn

class ShizukuFileSystem {
    fun isRestoreWriteEnabled(state: ShizukuState): Boolean =
        state == ShizukuState.READY && Wuwa36SafetyPolicy.RESTORE_WRITE_ENABLED

    fun patchWriteStatus(state: ShizukuState): String =
        if (state == ShizukuState.READY) {
            "PAK-only gated"
        } else {
            "locked"
        }

    fun configPresetWriteStatus(state: ShizukuState): String =
        if (state == ShizukuState.READY) {
            "locked for WUWA 3.6"
        } else {
            "locked"
        }

    fun disabledReason(state: ShizukuState): String {
        if (state != ShizukuState.READY) {
            return "Shizuku is not ready yet."
        }
        return "Patch writing is available only through the verified WUWA 3.6 PAK + SIG + MountLang transaction. Config preset and general restore writes are locked in this release."
    }
}
