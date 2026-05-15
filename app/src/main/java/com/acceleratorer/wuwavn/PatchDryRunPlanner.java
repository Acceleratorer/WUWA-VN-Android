package com.acceleratorer.wuwavn;

import android.content.Context;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PatchDryRunPlanner {
    private static final String ENGINE_INI = "UE4Game/Client/Client/Saved/Config/Android/Engine.ini";
    private static final String DEVICE_PROFILES_INI = "UE4Game/Client/Client/Saved/Config/Android/DeviceProfiles.ini";
    private static final String MOUNT_LANG = "UE4Game/Client/Client/Saved/Config/Android/MountLang_en.txt";
    private static final String PATCH_PAK = "UE4Game/Client/Client/Content/Paks/WuWaVH_99_P.pak";

    private static final List<String> BACKUP_RELATIVE_PATHS = Collections.unmodifiableList(Arrays.asList(
            ENGINE_INI,
            DEVICE_PROFILES_INI,
            MOUNT_LANG
    ));

    private static final Set<String> ALLOWED_TARGETS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ENGINE_INI,
            DEVICE_PROFILES_INI,
            MOUNT_LANG,
            PATCH_PAK
    )));

    private final BackupManager backupManager;

    PatchDryRunPlanner(BackupManager backupManager) {
        this.backupManager = backupManager;
    }

    PatchDryRun plan(Context context) {
        assertAllowed(PATCH_PAK);
        for (String relativePath : BACKUP_RELATIVE_PATHS) {
            assertAllowed(relativePath);
        }

        List<String> filesToAdd = Collections.singletonList("WuWaVH_99_P.pak");
        List<String> filesToModify = Arrays.asList("Engine.ini", "DeviceProfiles.ini", "MountLang_en.txt");
        List<String> metadataFiles = Arrays.asList(
                "Engine.ini",
                "DeviceProfiles.ini",
                "MountLang_en.txt",
                "WuWaVH_99_P.pak"
        );
        File backupDirectory = backupManager.planBackupSession(context);
        return new PatchDryRun(
                filesToAdd,
                filesToModify,
                metadataFiles,
                backupDirectory
        );
    }

    static boolean isAllowedTarget(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        return !normalized.contains("..") && ALLOWED_TARGETS.contains(normalized);
    }

    static List<String> backupRelativePaths() {
        return BACKUP_RELATIVE_PATHS;
    }

    static String displayName(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static void assertAllowed(String relativePath) {
        if (!isAllowedTarget(relativePath)) {
            throw new SecurityException("Blocked unsafe target path: " + relativePath);
        }
    }
}
