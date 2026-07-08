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
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.progresskeeper.ProgressListener;
import com.cannon.onyxlauncher.progresskeeper.ProgressKeeper;
import com.cannon.onyxlauncher.progresskeeper.TaskCountListener;
import com.cannon.onyxlauncher.utils.NotificationUtils;
import com.kdt.mcgui.ProgressLayout;

/**
 * Lazy service which allows the process not to get killed.
 * Can be created from context, can be killed statically
 */
public class ProgressService extends Service implements TaskCountListener, ProgressListener {
    private static final long NOTIFICATION_UPDATE_MIN_INTERVAL_MS = 750L;

    private NotificationManagerCompat notificationManagerCompat;
    private String currentProgressText;
    private int currentProgress;
    private long lastNotificationUpdateMs;
    private boolean progressListenerAttached;

    /** Simple wrapper to start the service */
    public static void startService(Context context){
        Intent intent = new Intent(context, ProgressService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    private NotificationCompat.Builder mNotificationBuilder;

    @Override
    public void onCreate() {
        Tools.buildNotificationChannel(getApplicationContext());
        notificationManagerCompat = NotificationManagerCompat.from(getApplicationContext());
        Intent killIntent = new Intent(getApplicationContext(), ProgressService.class);
        killIntent.putExtra("kill", true);
        PendingIntent pendingKillIntent = PendingIntent.getService(this, NotificationUtils.PENDINGINTENT_CODE_KILL_PROGRESS_SERVICE
                , killIntent, Build.VERSION.SDK_INT >=23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        mNotificationBuilder = new NotificationCompat.Builder(this, "channel_id")
                .setContentTitle(getString(R.string.lazy_service_default_title))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,  getString(R.string.notification_terminate), pendingKillIntent)
                .setSmallIcon(R.drawable.notif_icon)
                .setOnlyAlertOnce(true)
                .setNotificationSilent();
        currentProgressText = getString(R.string.progresslayout_tasks_in_progress, ProgressKeeper.getTaskCount());
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null) {
            if(intent.getBooleanExtra("kill", false)) {
                stopSelf(); // otherwise Android tries to restart the service since it "crashed"
                Process.killProcess(Process.myPid());
                return START_NOT_STICKY;
            }
        }
        Log.d("ProgressService", "Started!");
        mNotificationBuilder.setContentText(getString(R.string.progresslayout_tasks_in_progress, ProgressKeeper.getTaskCount()));
        Notification notification = mNotificationBuilder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationUtils.NOTIFICATION_ID_PROGRESS_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        } else {
            startForeground(NotificationUtils.NOTIFICATION_ID_PROGRESS_SERVICE, notification);
        }
        if(ProgressKeeper.getTaskCount() < 1) stopSelf();
        else {
            ProgressKeeper.addTaskCountListener(this, false);
            if(!progressListenerAttached) {
                ProgressKeeper.addListener(ProgressLayout.INSTALL_MODPACK, this);
                progressListenerAttached = true;
            }
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        ProgressKeeper.removeTaskCountListener(this);
        if(progressListenerAttached) {
            ProgressKeeper.removeListener(ProgressLayout.INSTALL_MODPACK, this);
            progressListenerAttached = false;
        }
    }

    @Override
    public void onUpdateTaskCount(int taskCount) {
        Tools.MAIN_HANDLER.post(()->{
            if(taskCount > 0) {
                mNotificationBuilder.setContentText(getString(R.string.progresslayout_tasks_in_progress, taskCount));
                notificationManagerCompat.notify(1, mNotificationBuilder.build());
            }else{
                stopSelf();
            }
        });
    }

    @Override
    public void onProgressStarted() {
        updateProgressNotification(currentProgress, currentProgressText, true);
    }

    @Override
    public void onProgressUpdated(int progress, int resid, Object... va) {
        String text = currentProgressText;
        if(resid != -1) {
            try {
                text = getString(resid, va);
            } catch(Exception ignored) {
                text = getString(R.string.status_installing);
            }
        } else if(va != null && va.length > 0 && va[0] instanceof String) {
            text = (String) va[0];
        }
        updateProgressNotification(progress, text, progress < 0 || progress > 100);
    }

    @Override
    public void onProgressEnded() {
        mNotificationBuilder.setProgress(0, 0, false);
    }

    private void updateProgressNotification(int progress, String text, boolean indeterminate) {
        Tools.MAIN_HANDLER.post(() -> {
            currentProgress = Math.max(0, Math.min(100, progress));
            currentProgressText = text;
            long now = SystemClock.elapsedRealtime();
            if(currentProgress > 0 && currentProgress < 100 &&
                    now - lastNotificationUpdateMs < NOTIFICATION_UPDATE_MIN_INTERVAL_MS) {
                return;
            }
            lastNotificationUpdateMs = now;
            mNotificationBuilder
                    .setContentText(text)
                    .setProgress(100, currentProgress, indeterminate);
            notificationManagerCompat.notify(NotificationUtils.NOTIFICATION_ID_PROGRESS_SERVICE, mNotificationBuilder.build());
        });
    }
}
