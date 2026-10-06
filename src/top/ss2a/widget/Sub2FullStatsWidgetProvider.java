package top.ss2a.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

public class Sub2FullStatsWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_MANUAL_REFRESH = "top.ss2a.widget.ACTION_MANUAL_REFRESH";

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Sub2RealtimeService.start(context);
        Sub2WidgetProvider.scheduleAutoAlarm(context);
        Sub2JobService.schedulePeriodicJob(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Sub2RealtimeService.start(context);
        Sub2WidgetProvider.scheduleAutoAlarm(context);
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
            ComponentName cn = new ComponentName(context, Sub2FullStatsWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids != null && ids.length > 0) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sub2_full_stats);
                views.setTextViewText(R.id.tv_update_time, "⚡ 同步中...");
                views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFF38BDF8);
                mgr.partiallyUpdateAppWidget(ids, views);
            }
        }
    }

    public static void updateAllWidgets(Context context, Sub2DashboardData data) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, Sub2FullStatsWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids == null || ids.length == 0) return;
        for (int id : ids) {
            renderWidget(context, mgr, id, data);
        }
    }

    private static void renderWidget(Context context, AppWidgetManager mgr, int id, Sub2DashboardData data) {
        WidgetDataStore store = new WidgetDataStore(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sub2_full_stats);

        String title = "SS2A · " + (data.modeTitle != null ? data.modeTitle : "全部用量");
        views.setTextViewText(R.id.tv_title, title);
        views.setTextViewText(R.id.tv_update_time, "更新于 " + data.lastUpdateTime);

        // 1. 今日用量
        views.setTextViewText(R.id.tv_today_cost, data.todayCost);
        views.setTextViewText(R.id.tv_today_requests, data.todayRequests + "次");
        views.setTextViewText(R.id.tv_today_tokens, data.todayTokens + " Tok");

        // 2. 累计全部用量
        views.setTextViewText(R.id.tv_total_cost, data.totalCost);
        views.setTextViewText(R.id.tv_total_requests, data.totalRequests + "次");
        views.setTextViewText(R.id.tv_total_tokens, data.totalTokens + " Tok");

        // 3. 实时速率与负载
        views.setTextViewText(R.id.tv_rpm, "RPM: " + data.rpm);
        views.setTextViewText(R.id.tv_tpm, "TPM: " + data.tpm);
        views.setTextViewText(R.id.tv_avg_duration, "延迟: " + data.avgDuration);
        views.setTextViewText(R.id.tv_active_keys, "Key: " + data.activeKeys);

        // 4. 成功/错误状态反馈
        if (data.isSuccess) {
            views.setViewVisibility(R.id.tv_error_msg, View.GONE);
            views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFF10B981);
        } else {
            views.setViewVisibility(R.id.tv_error_msg, View.VISIBLE);
            views.setTextViewText(R.id.tv_error_msg, data.errorMessage != null && !data.errorMessage.isEmpty() ? data.errorMessage : "同步异常");
            views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFFEF4444);
        }

        // 5. 点击交互事件
        Intent refreshIntent = new Intent(context, Sub2WidgetProvider.class);
        refreshIntent.setAction(Sub2WidgetProvider.ACTION_MANUAL_REFRESH);
        PendingIntent pRefresh = PendingIntent.getBroadcast(
            context, 10, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_refresh, pRefresh);

        Intent settingsIntent = new Intent(context, MainActivity.class);
        PendingIntent pSettings = PendingIntent.getActivity(
            context, 11, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_settings, pSettings);

        String targetUrl = store.getServerUrl();
        if ("全站监控".equals(data.modeTitle)) {
            if (!targetUrl.endsWith("/admin")) targetUrl += "/admin";
        }
        Intent openWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
        openWeb.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pWeb = PendingIntent.getActivity(
            context, 12, openWeb, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, pWeb);

        mgr.updateAppWidget(id, views);
    }
}