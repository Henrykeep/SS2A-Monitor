package top.ss2a.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

public class Sub2WidgetProvider extends AppWidgetProvider {
    public static final String ACTION_MANUAL_REFRESH = "top.ss2a.widget.ACTION_MANUAL_REFRESH";
    public static final String ACTION_AUTO_ALARM_TICK = "top.ss2a.widget.ACTION_AUTO_ALARM_TICK";
    private static final long AUTO_SYNC_INTERVAL_MS = 60 * 1000L; // 闹钟兜底1分钟

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Sub2RealtimeService.start(context);
        scheduleAutoAlarm(context);
        Sub2JobService.schedulePeriodicJob(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        cancelAutoAlarm(context);
        Sub2JobService.cancelJob(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Sub2RealtimeService.start(context);
        scheduleAutoAlarm(context);
        Sub2JobService.schedulePeriodicJob(context);
        WidgetDataStore store = new WidgetDataStore(context);
        Sub2DashboardData cached = store.getCachedStats();
        for (int id : appWidgetIds) {
            renderWidget(context, appWidgetManager, id, cached);
        }
        Sub2RealtimeService.syncNow(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        if (ACTION_MANUAL_REFRESH.equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, Sub2WidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sub2_4x2);
            views.setTextViewText(R.id.tv_update_time, "⚡ 同步中...");
            mgr.partiallyUpdateAppWidget(ids, views);

            Sub2RealtimeService.start(context);
            Sub2RealtimeService.syncNow(context);
            scheduleAutoAlarm(context);
        } else if (ACTION_AUTO_ALARM_TICK.equals(action) || 
                   Intent.ACTION_USER_PRESENT.equals(action) || 
                   Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                   "android.intent.action.MY_PACKAGE_REPLACED".equals(action) ||
                   "android.net.conn.CONNECTIVITY_CHANGE".equals(action) ||
                   Intent.ACTION_POWER_CONNECTED.equals(action) ||
                   Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            Sub2RealtimeService.start(context);
            Sub2RealtimeService.syncNow(context);
            scheduleAutoAlarm(context);
        }
    }

    public static void scheduleAutoAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent i = new Intent(context, Sub2WidgetProvider.class);
                i.setAction(ACTION_AUTO_ALARM_TICK);
                PendingIntent pi = PendingIntent.getBroadcast(
                    context, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                long triggerAt = SystemClock.elapsedRealtime() + AUTO_SYNC_INTERVAL_MS;
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            }
        } catch (Exception ignored) {}
    }

    public static void cancelAutoAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent i = new Intent(context, Sub2WidgetProvider.class);
                i.setAction(ACTION_AUTO_ALARM_TICK);
                PendingIntent pi = PendingIntent.getBroadcast(
                    context, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.cancel(pi);
            }
        } catch (Exception ignored) {}
    }

    public static void updateAllWidgets(Context context, Sub2DashboardData data) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, Sub2WidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        for (int id : ids) {
            renderWidget(context, mgr, id, data);
        }
    }

    private static void renderWidget(Context context, AppWidgetManager mgr, int id, Sub2DashboardData data) {
        WidgetDataStore store = new WidgetDataStore(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sub2_4x2);
        String title = "SS2A · " + (data.modeTitle != null ? data.modeTitle : "全站监控");
        views.setTextViewText(R.id.tv_title, title);
        views.setTextViewText(R.id.tv_today_cost, data.todayCost);
        views.setTextViewText(R.id.tv_today_requests, data.todayRequests + " 次");
        views.setTextViewText(R.id.tv_today_tokens, data.todayTokens + " Tokens");
        views.setTextViewText(R.id.tv_update_time, "更新于 " + data.lastUpdateTime);

        if (data.isSuccess) {
            views.setViewVisibility(R.id.tv_error_msg, View.GONE);
            views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFF10B981);
        } else {
            views.setViewVisibility(R.id.tv_error_msg, View.VISIBLE);
            views.setTextViewText(R.id.tv_error_msg, data.errorMessage != null && !data.errorMessage.isEmpty() ? data.errorMessage : "同步异常");
            views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFFEF4444);
        }

        Intent refreshIntent = new Intent(context, Sub2WidgetProvider.class);
        refreshIntent.setAction(ACTION_MANUAL_REFRESH);
        PendingIntent pRefresh = PendingIntent.getBroadcast(
            context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_refresh, pRefresh);

        String targetUrl = store.getServerUrl();
        if ("全站监控".equals(data.modeTitle)) {
            if (!targetUrl.endsWith("/admin")) targetUrl += "/admin";
        }
        Intent openWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
        openWeb.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pWeb = PendingIntent.getActivity(
            context, 1, openWeb, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, pWeb);

        Intent settingsIntent = new Intent(context, MainActivity.class);
        PendingIntent pSettings = PendingIntent.getActivity(
            context, 2, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_settings, pSettings);

        mgr.updateAppWidget(id, views);
    }
}
