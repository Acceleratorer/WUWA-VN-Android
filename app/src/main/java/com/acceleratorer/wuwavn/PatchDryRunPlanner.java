package com.acceleratorer.wuwavn;

import android.content.Context;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class PatchDryRunPlanner {
    private static final Set<String> ALLOWED_TARGETS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "UE4Game/Client/Client/Saved/Config/Android/Engine.ini",
            "UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini",
            "UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt",
            "UE4Game/Client/Client/Content/Paks/WuWaVH_99_P.pak"
    )));

    private final BackupManager backupManager;

    PatchDryRunPlanner(BackupManager backupManager) {
        this.backupManager = backupManager;
    }

    PatchDryRun plan(Context context) {
        assertAllowed("UE4Game/Client/Client/Content/Paks/WuWaVH_99_P.pak");
        assertAllowed("UE4Game/Client/Client/Saved/Config/Android/Engine.ini");
        assertAllowed("UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini");
        assertAllowed("UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt");

        File backupDirectory = backupManager.planBackupSession(context);
        return new PatchDryRun(
                Collections.singletonList("WuWaVH_99_P.pak"),
                Arrays.asList("Engine.ini", "DeviceProfiles.ini", "MountLang_en.txt"),
                backupDirectory
        );
    }

    static boolean isAllowedTarget(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        return !normalized.contains("..") && ALLOWED_TARGETS.contains(normalized);
    }

    private static void assertAllowed(String relativePath) {
        if (!isAllowedTarget(relativePath)) {
            throw new SecurityException("Blocked unsafe target path: " + relativePath);
        }
    }
}
