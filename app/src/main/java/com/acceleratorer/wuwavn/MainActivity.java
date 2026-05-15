package com.acceleratorer.wuwavn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private final DebugLogger logger = new DebugLogger();
    private final GamePackageDetector gamePackageDetector = new GamePackageDetector();
    private final ShizukuStateChecker shizukuStateChecker = new ShizukuStateChecker(gamePackageDetector);
    private final BackupManager backupManager = new BackupManager();
    private final PatchDryRunPlanner dryRunPlanner = new PatchDryRunPlanner(backupManager);
    private final PatchManifestRepository manifestRepository = new PatchManifestRepository();
    private final ShizukuFileSystem shizukuFileSystem = new ShizukuFileSystem();
    private final ShizukuBackupReader backupReader = new ShizukuBackupReader(backupManager);
    private final DownloadClient downloadClient = new DownloadClient();

    private TextView statusView;
    private TextView logView;
    private volatile boolean patchPreparationRunning;
    private volatile boolean backupRunning;
    private ShizukuState shizukuState = ShizukuState.NOT_INSTALLED;
    private GamePackageDetector.State gameState = GamePackageDetector.State.NOT_INSTALLED;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = new Shizuku.OnBinderReceivedListener() {
        @Override
        public void onBinderReceived() {
            logger.add("Shizuku: binder received");
            refreshStatus();
        }
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = new Shizuku.OnBinderDeadListener() {
        @Override
        public void onBinderDead() {
            logger.add("Shizuku: binder dead");
            refreshStatus();
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    logger.add("Shizuku permission result: " + grantResult);
                    refreshStatus();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(11, 17, 29));
        getWindow().setNavigationBarColor(Color.rgb(11, 17, 29));

        setContentView(createContentView());
        logger.setListener(new DebugLogger.Listener() {
            @Override
            public void onLogChanged(String text) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (logView != null) {
                            logView.setText(text);
                        }
                    }
                });
            }
        });

        registerShizukuListeners();
        logger.add("App version: " + AppConstants.VERSION_NAME + " (" + AppConstants.VERSION_CODE + ")");
        logger.add("Android version: " + Build.VERSION.RELEASE);
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
            Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        } catch (Throwable ignored) {
        }
    }

    private View createContentView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(11, 17, 29));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("WUWA VN Android", 26, Color.WHITE, true);
        root.addView(title);

        TextView subtitle = text("Safe patch manager for Vietnamese Wuthering Waves players.", 14, Color.rgb(182, 193, 211), false);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle);

        statusView = text("", 15, Color.WHITE, false);
        statusView.setPadding(dp(14), dp(14), dp(14), dp(14));
        statusView.setBackgroundColor(Color.rgb(22, 32, 49));
        root.addView(statusView, matchWrap());

        root.addView(space(14));
        root.addView(primaryButton("Show Patch Plan", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPatchDryRun();
            }
        }));
        root.addView(button("Backup Game Configs", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                backupGameConfigs();
            }
        }));
        root.addView(button("Download & Verify Patch", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                preparePatchSafely();
            }
        }));
        root.addView(button("Update Vietnamese Patch", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openUrl(AppConstants.RELEASES_URL);
                logger.add("Update check: opened GitHub Releases");
            }
        }));
        root.addView(button("Restore Original Files", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showMessage("Restore Original Files", "Restore is locked until backup copy and Shizuku file writing are tested on a real device.");
                logger.add("Restore: locked");
            }
        }));
        root.addView(button("Check Game Folder", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshStatus();
                logger.add("Game folder: checked package state");
            }
        }));
        root.addView(button("Open Shizuku", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openOrRequestShizuku();
            }
        }));

        root.addView(space(18));
        TextView safetyTitle = text("Safety rules", 18, Color.WHITE, true);
        root.addView(safetyTitle);
        TextView safety = text(
                "Only allowlisted WUWA targets can be planned:\n" +
                        "- Engine.ini\n" +
                        "- DeviceProfiles.ini\n" +
                        "- MountLang_en.txt\n" +
                        "- WuWaVH_99_P.pak\n\n" +
                        "Always backup first. Never use this app for cheating, anti-cheat bypass, or gameplay manipulation.",
                14,
                Color.rgb(198, 207, 220),
                false
        );
        safety.setPadding(0, dp(8), 0, dp(12));
        root.addView(safety);

        TextView logTitle = text("Debug log", 18, Color.WHITE, true);
        root.addView(logTitle);
        logView = text("", 12, Color.rgb(209, 218, 230), false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        logView.setBackgroundColor(Color.rgb(7, 12, 21));
        root.addView(logView, matchWrap());

        root.addView(button("Copy Debug Log", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyLog();
            }
        }));
        root.addView(button("Send Issue Report", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareLog();
            }
        }));

        return scroll;
    }

    private void registerShizukuListeners() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
            Shizuku.addRequestPermissionResultListener(permissionResultListener);
        } catch (Throwable throwable) {
            logger.add("Shizuku listeners: unavailable");
        }
    }

    private void refreshStatus() {
        gameState = gamePackageDetector.detect(this);
        shizukuState = shizukuStateChecker.check(this);
        PatchManifest manifest = manifestRepository.current();

        statusView.setText(
                "Status\n" +
                        "Game: " + gameState.label() + "\n" +
                        "Shizuku: " + shizukuState.label() + "\n" +
                        "Patch: " + manifest.patchVersion + "\n" +
                        "Patch SHA-256: " + manifest.pakSha256.substring(0, 12) + "...\n" +
                        "Mode: Safe / Default\n" +
                        "File writing: " + (shizukuFileSystem.isWriteEnabled(shizukuState) ? "enabled" : "locked")
        );
    }

    private void showPatchDryRun() {
        logger.add("Dry run: started");
        try {
            PatchDryRun dryRun = dryRunPlanner.plan(this);
            String message = dryRun.describe() + "\n\n" + shizukuFileSystem.disabledReason(shizukuState);
            if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
                message = "Global Wuthering Waves package is not detected.\n\n" + message;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Dry run")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
            logger.add("Dry run: allowlist verified");
            logger.add("Backup target planned: " + dryRun.backupDirectory.getAbsolutePath());
        } catch (RuntimeException exception) {
            showMessage("Dry run failed", exception.getMessage());
            logger.add("Dry run: failed - " + exception.getMessage());
        }
    }

    private void preparePatchSafely() {
        if (patchPreparationRunning) {
            Toast.makeText(this, "Patch preparation is already running.", Toast.LENGTH_SHORT).show();
            return;
        }

        refreshStatus();
        patchPreparationRunning = true;
        logger.add("Patch preparation: started");

        new Thread(new Runnable() {
            @Override
            public void run() {
                PatchManifest manifest = manifestRepository.current();
                try {
                    dryRunPlanner.plan(MainActivity.this);
                    logger.add("Dry run: allowlist verified before download");

                    File patchFile = downloadClient.downloadAndVerify(
                            MainActivity.this,
                            manifest,
                            new DownloadClient.ProgressListener() {
                                @Override
                                public void onProgress(String message) {
                                    logger.add(message);
                                }
                            }
                    );

                    logger.add("Patch file: " + patchFile.getAbsolutePath());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showMessage(
                                    "Patch verified",
                                    "Patch was downloaded and verified successfully.\n\nUse Backup Game Configs to test read-only Shizuku backup. Game file writing is still locked."
                            );
                        }
                    });
                } catch (Exception exception) {
                    logger.add("Patch preparation failed: " + exception.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showMessage("Patch preparation failed", exception.getMessage());
                        }
                    });
                } finally {
                    patchPreparationRunning = false;
                }
            }
        }).start();
    }

    private void backupGameConfigs() {
        if (backupRunning) {
            Toast.makeText(this, "Backup is already running.", Toast.LENGTH_SHORT).show();
            return;
        }

        refreshStatus();
        if (gameState != GamePackageDetector.State.GLOBAL_INSTALLED) {
            showMessage("Backup blocked", "Wuthering Waves Global is not detected. Install the Global version before backing up game config files.");
            logger.add("Read-only backup: blocked - WUWA Global not detected");
            return;
        }
        if (shizukuState != ShizukuState.READY) {
            showMessage("Backup blocked", shizukuFileSystem.disabledReason(shizukuState));
            logger.add("Read-only backup: blocked - Shizuku not ready");
            return;
        }

        backupRunning = true;
        logger.add("Read-only backup: started");
        final GamePackageDetector.State detectedGameState = gameState;

        new Thread(new Runnable() {
            @Override
            public void run() {
                PatchManifest manifest = manifestRepository.current();
                try {
                    File backupDirectory = backupManager.createBackupDirectory(MainActivity.this);
                    logger.add("Backup path: " + backupDirectory.getAbsolutePath());

                    ShizukuBackupReader.BackupResult result = backupReader.backupConfigFiles(
                            MainActivity.this,
                            backupDirectory,
                            logger
                    );
                    backupManager.writeBackupMetadata(backupDirectory, manifest, detectedGameState, result.backedUpFiles, result.missingFiles);
                    logger.add("Backup metadata: wrote actual backed-up files");
                    logger.add("Read-only backup: success");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showMessage("Backup complete", backupSummary(backupDirectory, result));
                        }
                    });
                } catch (Exception exception) {
                    logger.add("Read-only backup failed: " + exception.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showMessage("Backup failed", exception.getMessage());
                        }
                    });
                } finally {
                    backupRunning = false;
                }
            }
        }).start();
    }

    private String backupSummary(File backupDirectory, ShizukuBackupReader.BackupResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("Backed up files:\n");
        for (BackupFileInfo file : result.backedUpFiles) {
            builder.append("- ")
                    .append(file.displayName)
                    .append(" (")
                    .append(file.sizeBytes)
                    .append(" bytes, SHA-256 ")
                    .append(file.sha256.substring(0, 12))
                    .append("...)\n");
        }
        if (!result.missingFiles.isEmpty()) {
            builder.append("\nMissing files:\n");
            for (String file : result.missingFiles) {
                builder.append("- ").append(file).append('\n');
            }
        }
        builder.append("\nBackup folder:\n").append(backupDirectory.getAbsolutePath());
        builder.append("\n\nThis version only reads game files. Patch writing and restore writing remain locked.");
        return builder.toString();
    }

    private void openOrRequestShizuku() {
        if (shizukuState == ShizukuState.RUNNING_PERMISSION_DENIED && shizukuStateChecker.requestPermissionIfPossible(this)) {
            logger.add("Shizuku: permission request sent");
            return;
        }

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(AppConstants.SHIZUKU_PACKAGE);
        if (launchIntent != null) {
            startActivity(launchIntent);
            logger.add("Shizuku: opened app");
            return;
        }
        openUrl("https://shizuku.rikka.app/");
        logger.add("Shizuku: opened install guide");
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, "No browser app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyLog() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("WUWA VN debug log", logger.text()));
            Toast.makeText(this, "Debug log copied.", Toast.LENGTH_SHORT).show();
            logger.add("Log: copied");
        }
    }

    private void shareLog() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "WUWA VN issue report");
        intent.putExtra(Intent.EXTRA_TEXT, logger.text());
        startActivity(Intent.createChooser(intent, "Send Issue Report"));
        logger.add("Issue report: share sheet opened");
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setLineSpacing(dp(2), 1.0f);
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return textView;
    }

    private Button primaryButton(String label, View.OnClickListener listener) {
        Button button = button(label, listener);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(25, 118, 210));
        return button;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.rgb(35, 48, 68));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private View space(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
