package top.ss2a.widget;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;

public class Sub2RealtimeService extends Service {
    public static final String ACTION_SYNC_NOW = "top.ss2a.widget.ACTION_SYNC_NOW";
    private static final String CHANNEL_ID = "ss2a_monitor_service";
    private static final int NOTIFICATION_ID = 9527;

    private Handler handler;
    private Runnable pollRunnable;
    private BroadcastReceiver screenReceiver;
    private boolean isScreenOn = true;
    private boolean isRunning = false;
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForegroundNotification("正在保持桌面小组件秒级实时同步");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            isScreenOn = pm.isInteractive();
        }

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                    isScreenOn = true;
                    triggerAsyncSync(false);
                    scheduleNextPoll();
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    isScreenOn = false;
                    scheduleNextPoll();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }

        startPollingLoop();
    }

    private void startPollingLoop() {
        if (isRunning) return;
        isRunning = true;
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    if (isScreenOn) {
                        triggerAsyncSync(false);
                    }
                    scheduleNextPoll();
                }
            }
        };
        triggerAsyncSync(false);
        scheduleNextPoll();
    }

    private void scheduleNextPoll() {
        if (handler != null && pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
            WidgetDataStore store = new WidgetDataStore(this);
            int intervalSec = store.getRefreshIntervalSeconds();
            if (intervalSec < 5) intervalSec = 5;
            long delayMs;
            if (isScreenOn) {
                delayMs = intervalSec * 1000L;
            } else {
                delayMs = Math.max(intervalSec * 1000L, 120 * 1000L);
            }
            handler.postDelayed(pollRunnable, delayMs);
        }
    }

    private void triggerAsyncSync(final boolean forced) {
        if (!isSyncing.compareAndSet(false, true)) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WidgetDataStore store = new WidgetDataStore(getApplicationContext());
                    Sub2ApiClient client = new Sub2ApiClient(store);
                    Sub2DashboardData fresh = client.fetchDashboardStats(forced);
                    java.util.List<Sub2LogItem> logs = client.fetchRecentLogs(4);
                    if (logs != null && !logs.isEmpty()) {
                        store.saveRecentLogs(logs);
                    } else {
                        logs = store.getCachedRecentLogs();
                    }
                    Sub2WidgetProvider.updateAllWidgets(getApplicationContext(), fresh);
                    Sub2FullStatsWidgetProvider.updateAllWidgets(getApplicationContext(), fresh);
                    Sub2LogsWidgetProvider.updateAllWidgets(getApplicationContext(), fresh, logs);
                    Sub2LatencyWidgetProvider.updateAllWidgets(getApplicationContext(), fresh, logs);
                    if (fresh.isSuccess) {
                        updateForegroundNotification("今日: " + fresh.todayCost + " | 累计: " + fresh.totalCost + " | " + fresh.todayRequests + "次 (" + fresh.lastUpdateTime + ")");
                    } else if (fresh.errorMessage != null && !fresh.errorMessage.isEmpty()) {
                        updateForegroundNotification("SS2A 监控: " + fresh.errorMessage);
                    }
                } catch (Exception ignored) {
                } finally {
                    isSyncing.set(false);
                }
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startPollingLoop();
        if (intent != null && ACTION_SYNC_NOW.equals(intent.getAction())) {
            triggerAsyncSync(true);
        } else {
            triggerAsyncSync(false);
        }
        scheduleNextPoll();
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // 当用户在任务管理器中划掉 App 界面时，利用 Alarm 立即安排复活拉起前台服务
        try {
            Intent restartIntent = new Intent(getApplicationContext(), Sub2RealtimeService.class);
            restartIntent.setAction(ACTION_SYNC_NOW);
            PendingIntent pi;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pi = PendingIntent.getForegroundService(
                    getApplicationContext(), 1002, restartIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pi = PendingIntent.getService(
                    getApplicationContext(), 1002, restartIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                long triggerAt = SystemClock.elapsedRealtime() + 1000L;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                } else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                }
            }
            Sub2WidgetProvider.scheduleAutoAlarm(getApplicationContext());
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (handler != null && pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
        }
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {}
        }
        try {
            Sub2WidgetProvider.scheduleAutoAlarm(getApplicationContext());
        } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SS2A 实时监控保活",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保证桌面小组件能够极速高频获取最新数据");
            channel.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundNotification(String text) {
        Notification notification = buildNotification(text);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateForegroundNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIFICATION_ID, buildNotification(text));
            }
        } catch (Exception ignored) {}
    }

    private Notification buildNotification(String text) {
        Intent notifyIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent syncIntent = new Intent(this, Sub2RealtimeService.class);
        syncIntent.setAction(ACTION_SYNC_NOW);
        PendingIntent pSync;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pSync = PendingIntent.getForegroundService(
                this, 1, syncIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pSync = PendingIntent.getService(
                this, 1, syncIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("SS2A 实时监控 · 正常运行")
               .setContentText(text)
               .setSmallIcon(android.R.drawable.ic_popup_sync)
               .setContentIntent(pi)
               .setOngoing(true)
               .addAction(android.R.drawable.ic_popup_sync, "⚡ 立即刷新", pSync);

        return builder.build();
    }

    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, Sub2RealtimeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {}
    }

    public static void syncNow(Context context) {
        try {
            Intent intent = new Intent(context, Sub2RealtimeService.class);
            intent.setAction(ACTION_SYNC_NOW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {}
    }
}
