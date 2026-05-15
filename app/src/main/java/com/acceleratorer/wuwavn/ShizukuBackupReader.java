package com.acceleratorer.wuwavn;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Environment;
import android.os.IBinder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import rikka.shizuku.Shizuku;

final class ShizukuBackupReader {
    private static final int MAX_CONFIG_BYTES = 512 * 1024;

    private final BackupManager backupManager;

    ShizukuBackupReader(BackupManager backupManager) {
        this.backupManager = backupManager;
    }

    BackupResult backupConfigFiles(Context context, File backupDirectory, DebugLogger logger) throws Exception {
        AtomicReference<IWuwaBackupService> serviceRef = new AtomicReference<>();
        CountDownLatch connected = new CountDownLatch(1);
        ComponentName componentName = new ComponentName(context, WuwaBackupUserService.class);
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(componentName)
                .daemon(false)
                .debuggable(false)
                .processNameSuffix("backup")
                .tag("backup")
                .version(AppConstants.VERSION_CODE);

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                serviceRef.set(IWuwaBackupService.Stub.asInterface(service));
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                serviceRef.set(null);
            }
        };

        try {
            Shizuku.bindUserService(args, connection);
            if (!connected.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while connecting Shizuku backup service.");
            }

            IWuwaBackupService service = serviceRef.get();
            if (service == null) {
                throw new IllegalStateException("Shizuku backup service did not connect.");
            }

            List<BackupFileInfo> backedUpFiles = new ArrayList<>();
            List<String> missingFiles = new ArrayList<>();
            for (String relativePath : PatchDryRunPlanner.backupRelativePaths()) {
                String absolutePath = gameAbsolutePath(relativePath);
                String displayName = PatchDryRunPlanner.displayName(relativePath);
                if (!service.exists(absolutePath)) {
                    logger.add("Backup read: missing " + displayName);
                    missingFiles.add(displayName);
                    continue;
                }

                long length = service.length(absolutePath);
                if (length > MAX_CONFIG_BYTES) {
                    throw new IllegalStateException(displayName + " is too large for safe read-only backup.");
                }

                byte[] bytes = service.readFile(absolutePath, MAX_CONFIG_BYTES);
                BackupFileInfo info = backupManager.writeBackedUpFile(backupDirectory, displayName, relativePath, bytes);
                backedUpFiles.add(info);
                logger.add("Backup read: copied " + displayName + " (" + info.sizeBytes + " bytes, sha256 " + info.sha256.substring(0, 12) + "...)");
            }

            if (backedUpFiles.isEmpty()) {
                throw new IllegalStateException("No allowlisted WUWA config files were backed up.");
            }

            return new BackupResult(backedUpFiles, missingFiles);
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, true);
            } catch (Throwable ignored) {
            }
        }
    }

    private String gameAbsolutePath(String relativePath) {
        return Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/Android/data/"
                + AppConstants.GLOBAL_GAME_PACKAGE
                + "/files/"
                + relativePath;
    }

    static final class BackupResult {
        final List<BackupFileInfo> backedUpFiles;
        final List<String> missingFiles;

        BackupResult(List<BackupFileInfo> backedUpFiles, List<String> missingFiles) {
            this.backedUpFiles = backedUpFiles;
            this.missingFiles = missingFiles;
        }
    }
}
