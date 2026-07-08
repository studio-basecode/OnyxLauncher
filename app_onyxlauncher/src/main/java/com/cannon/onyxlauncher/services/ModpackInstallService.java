package com.cannon.onyxlauncher.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.cannon.onyxlauncher.OnyxApplication;
import com.cannon.onyxlauncher.OnyxMainActivity;
import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.modloaders.ModloaderDownloadListener;
import com.cannon.onyxlauncher.modloaders.modpacks.api.CommonApi;
import com.cannon.onyxlauncher.modloaders.modpacks.api.ModLoader;
import com.cannon.onyxlauncher.modloaders.modpacks.api.NotificationDownloadListener;
import com.cannon.onyxlauncher.modloaders.modpacks.models.ModDetail;
import com.cannon.onyxlauncher.progresskeeper.ProgressKeeper;
import com.cannon.onyxlauncher.progresskeeper.ProgressListener;
import com.cannon.onyxlauncher.utils.NotificationUtils;
import com.kdt.mcgui.ProgressLayout;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModpackInstallService extends Service implements ProgressListener {
    private static final String TAG = "ModpackInstallService";
    private static final String ACTION_INSTALL = "com.cannon.onyxlauncher.action.INSTALL_MODPACK";
    public static final String ACTION_INSTALL_FINISHED = "com.cannon.onyxlauncher.action.MODPACK_INSTALL_FINISHED";
    public static final String EXTRA_PROFILE_ID = "profile_id";
    public static final String EXTRA_DISPLAY_NAME = "display_name";
    public static final String EXTRA_VERSION_ID = "version_id";
    public static final String EXTRA_MINECRAFT_VERSION = "minecraft_version";
    private static final String EXTRA_MOD_DETAIL = "mod_detail";
    private static final String EXTRA_SELECTED_VERSION = "selected_version";
    private static final String EXTRA_CURSEFORGE_KEY = "curseforge_key";
    private static final long NOTIFICATION_UPDATE_MIN_INTERVAL_MS = 750L;
    private static final AtomicBoolean sInstallRunning = new AtomicBoolean(false);

    private NotificationCompat.Builder notificationBuilder;
    private NotificationManagerCompat notificationManager;
    private String currentText;
    private int currentProgress;
    private long lastNotificationUpdateMs;
    private boolean progressListenerAttached;

    public static void enqueueInstall(Context context, ModDetail detail, int selectedVersion, String curseforgeKey) {
        Intent intent = new Intent(context, ModpackInstallService.class);
        intent.setAction(ACTION_INSTALL);
        intent.putExtra(EXTRA_MOD_DETAIL, detail);
        intent.putExtra(EXTRA_SELECTED_VERSION, selectedVersion);
        intent.putExtra(EXTRA_CURSEFORGE_KEY, curseforgeKey);
        ContextCompat.startForegroundService(context.getApplicationContext(), intent);
    }

    @Override
    public void onCreate() {
        Tools.buildNotificationChannel(getApplicationContext());
        notificationManager = NotificationManagerCompat.from(getApplicationContext());
        currentText = getString(R.string.status_starting_download);
        notificationBuilder = new NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle(getString(R.string.modpack_install_notification_title))
                .setContentText(currentText)
                .setContentIntent(createContentIntent())
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, 0, true)
                .setNotificationSilent();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat(notificationBuilder.build());
        if (intent == null || !ACTION_INSTALL.equals(intent.getAction())) {
            stopIfIdle();
            return START_NOT_STICKY;
        }

        if (!sInstallRunning.compareAndSet(false, true)) {
            updateNotification(0, getString(R.string.modpack_background_already_running), true, true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        ModDetail detail = readModDetail(intent);
        int selectedVersion = intent.getIntExtra(EXTRA_SELECTED_VERSION, 0);
        String curseforgeKey = intent.getStringExtra(EXTRA_CURSEFORGE_KEY);
        if (detail == null) {
            finishWithError(new IOException("Missing modpack details"));
            sInstallRunning.set(false);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        attachProgressListener();
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.status_starting_download);
        OnyxApplication.sExecutorService.execute(() -> {
            try {
                ModLoader installedLoader = installModpack(detail, selectedVersion, curseforgeKey);
                finishSuccessfully(installedLoader);
            } catch (Exception e) {
                Log.e(TAG, "Background modpack install failed", e);
                finishWithError(e);
            } finally {
                detachProgressListener();
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                sInstallRunning.set(false);
                stopSelf(startId);
            }
        });

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        detachProgressListener();
        super.onDestroy();
    }

    @Override
    public void onProgressStarted() {
        updateNotification(0, currentText, true, true);
    }

    @Override
    public void onProgressUpdated(int progress, int resid, Object... va) {
        String text = currentText;
        if (resid != -1) {
            try {
                text = getString(resid, va);
            } catch (Exception ignored) {
                text = getString(R.string.status_installing);
            }
        } else if (va != null && va.length > 0 && va[0] instanceof String) {
            text = (String) va[0];
        }
        updateNotification(progress, text, progress < 0 || progress > 100, true);
    }

    @Override
    public void onProgressEnded() {
        updateNotification(currentProgress, currentText, false, true);
    }

    @SuppressLint("MissingPermission")
    private void updateNotification(int progress, String text, boolean indeterminate, boolean ongoing) {
        currentProgress = Math.max(0, Math.min(100, progress));
        currentText = text == null ? getString(R.string.status_installing) : text;
        long now = SystemClock.elapsedRealtime();
        boolean mustNotify = !ongoing || currentProgress == 0 || currentProgress >= 100;
        if (!mustNotify && now - lastNotificationUpdateMs < NOTIFICATION_UPDATE_MIN_INTERVAL_MS) {
            return;
        }
        lastNotificationUpdateMs = now;
        notificationBuilder
                .setContentText(currentText)
                .setOngoing(ongoing)
                .setProgress(100, currentProgress, indeterminate);
        notificationManager.notify(NotificationUtils.NOTIFICATION_ID_BACKGROUND_DOWNLOAD, notificationBuilder.build());
    }

    private ModLoader installModpack(ModDetail detail, int selectedVersion, String curseforgeKey) throws IOException {
        CommonApi api = new CommonApi(curseforgeKey == null ? "" : curseforgeKey);
        ModLoader modLoader = api.installMod(detail, selectedVersion);
        if (modLoader == null) {
            throw new IOException("Installer did not return valid loader data");
        }

        Exception[] loaderError = new Exception[1];
        NotificationDownloadListener notificationListener =
                new NotificationDownloadListener(getApplicationContext(), modLoader);
        ModloaderDownloadListener loaderListener = new ModloaderDownloadListener() {
            @Override
            public void onDownloadFinished(File downloadedFile) {
                if (modLoader.requiresGuiInstallation()) {
                    notificationListener.onDownloadFinished(downloadedFile);
                }
            }

            @Override
            public void onDataNotAvailable() {
                loaderError[0] = new IOException(getString(modLoader.requiresGuiInstallation()
                        ? R.string.no_forge_installer_for_version
                        : R.string.no_loader_for_version_err));
                if (modLoader.requiresGuiInstallation()) {
                    notificationListener.onDataNotAvailable();
                }
            }

            @Override
            public void onDownloadError(Exception e) {
                loaderError[0] = e;
                if (modLoader.requiresGuiInstallation()) {
                    notificationListener.onDownloadError(e);
                }
            }
        };

        Runnable loaderTask = modLoader.getDownloadTask(loaderListener);
        if (loaderTask != null && (modLoader.requiresGuiInstallation()
                || !isLoaderInstalled(modLoader.getVersionId()))) {
            loaderTask.run();
        }
        if (loaderError[0] != null) {
            if (loaderError[0] instanceof IOException) throw (IOException) loaderError[0];
            throw new IOException(loaderError[0]);
        }
        return modLoader;
    }

    private boolean isLoaderInstalled(String versionId) {
        if (versionId == null || versionId.isEmpty()) return false;
        File versionJson = new File(Tools.DIR_HOME_VERSION, versionId + "/" + versionId + ".json");
        return versionJson.isFile() && versionJson.length() > 0;
    }

    private void finishSuccessfully(ModLoader modLoader) {
        sendInstallFinishedBroadcast(modLoader);
        updateNotification(100, getString(R.string.modpack_installed_successfully), false, false);
        stopForegroundCompat(false);
    }

    private void sendInstallFinishedBroadcast(ModLoader modLoader) {
        if (modLoader == null) return;
        Intent intent = new Intent(ACTION_INSTALL_FINISHED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_PROFILE_ID, modLoader.profileId);
        intent.putExtra(EXTRA_DISPLAY_NAME, modLoader.displayName);
        intent.putExtra(EXTRA_VERSION_ID, modLoader.getVersionId());
        intent.putExtra(EXTRA_MINECRAFT_VERSION, modLoader.minecraftVersion);
        sendBroadcast(intent);
    }

    private void finishWithError(Exception e) {
        stopForegroundCompat(false);
        Tools.showErrorRemote(getApplicationContext(), R.string.modpack_install_download_failed, e);
    }

    private void attachProgressListener() {
        if (!progressListenerAttached) {
            ProgressKeeper.addListener(ProgressLayout.INSTALL_MODPACK, this);
            progressListenerAttached = true;
        }
    }

    private void detachProgressListener() {
        if (progressListenerAttached) {
            ProgressKeeper.removeListener(ProgressLayout.INSTALL_MODPACK, this);
            progressListenerAttached = false;
        }
    }

    private void stopIfIdle() {
        if (!sInstallRunning.get()) stopSelf();
    }

    private PendingIntent createContentIntent() {
        Intent activityIntent = new Intent(this, OnyxMainActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this,
                NotificationUtils.PENDINGINTENT_CODE_BACKGROUND_DOWNLOAD,
                activityIntent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private ModDetail readModDetail(Intent intent) {
        Object extra = intent.getSerializableExtra(EXTRA_MOD_DETAIL);
        if (extra instanceof ModDetail) return (ModDetail) extra;
        return null;
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationUtils.NOTIFICATION_ID_BACKGROUND_DOWNLOAD,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NotificationUtils.NOTIFICATION_ID_BACKGROUND_DOWNLOAD, notification);
        }
    }

    private void stopForegroundCompat(boolean removeNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(removeNotification ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(removeNotification);
        }
    }
}
