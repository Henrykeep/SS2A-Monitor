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
import java.util.List;

public class Sub2LogsWidgetProvider extends AppWidgetProvider {
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
        Sub2DashboardData cachedStats = store.getCachedStats();
        List<Sub2LogItem> cachedLogs = store.getCachedRecentLogs();
        for (int id : appWidgetIds) {
            renderWidget(context, appWidgetManager, id, cachedStats, cachedLogs);
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
            ComponentName cn = new ComponentName(context, Sub2LogsWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids != null && ids.length > 0) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sub2_logs);
                views.setTextViewText(R.id.tv_update_time, "⚡ 同步中...");
                mgr.partiallyUpdateAppWidget(ids, views);
            }
        }
    }

    public static void updateAllWidgets(Context context, Sub2DashboardData stats, List<Sub2LogItem> logs) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, Sub2LogsWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids == null || ids.length == 0) return;
        for (int id : ids) {
            renderWidget(context, mgr, id, stats, logs);
        }
    }

    private static void renderWidget(Context context, AppWidgetManager mgr, int id, Sub2DashboardData stats, List<Sub2LogItem> logs) {
        WidgetDataStore store = new WidgetDataStore(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sub2_logs);

        views.setTextViewText(R.id.tv_update_time, "更新于 " + (stats != null ? stats.lastUpdateTime : "--:--"));

        // 绑定底部汇总简报
        if (stats != null) {
            views.setTextViewText(R.id.tv_bottom_summary, "今日: " + stats.todayCost + " (" + stats.todayRequests + "次) · RPM: " + stats.rpm);
        }

        // 绑定 4 行最近调用记录
        int[] layoutIds = {R.id.layout_log_item_1, R.id.layout_log_item_2, R.id.layout_log_item_3, R.id.layout_log_item_4};
        int[] modelIds = {R.id.tv_log_model_1, R.id.tv_log_model_2, R.id.tv_log_model_3, R.id.tv_log_model_4};
        int[] tokenIds = {R.id.tv_log_tokens_1, R.id.tv_log_tokens_2, R.id.tv_log_tokens_3, R.id.tv_log_tokens_4};
        int[] costIds = {R.id.tv_log_cost_1, R.id.tv_log_cost_2, R.id.tv_log_cost_3, R.id.tv_log_cost_4};
        int[] timeIds = {R.id.tv_log_time_1, R.id.tv_log_time_2, R.id.tv_log_time_3, R.id.tv_log_time_4};

        if (logs != null && !logs.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                if (i < logs.size()) {
                    Sub2LogItem item = logs.get(i);
                    views.setViewVisibility(layoutIds[i], View.VISIBLE);
                    views.setTextViewText(modelIds[i], item.model);
                    views.setTextViewText(tokenIds[i], item.tokensSummary);
                    views.setTextViewText(costIds[i], item.cost);
                    views.setTextViewText(timeIds[i], item.time);
                } else {
                    views.setViewVisibility(layoutIds[i], View.GONE);
                }
            }
        } else {
            views.setTextViewText(modelIds[0], "暂无近期调用记录");
            views.setTextViewText(tokenIds[0], "--");
            views.setTextViewText(costIds[0], "$0.00");
            views.setTextViewText(timeIds[0], "--:--");
            views.setViewVisibility(layoutIds[0], View.VISIBLE);
            views.setViewVisibility(layoutIds[1], View.GONE);
            views.setViewVisibility(layoutIds[2], View.GONE);
            views.setViewVisibility(layoutIds[3], View.GONE);
        }

        // 成功状态或异常条
        if (stats != null && !stats.isSuccess) {
            views.setViewVisibility(R.id.tv_error_msg, View.VISIBLE);
            views.setTextViewText(R.id.tv_error_msg, stats.errorMessage != null && !stats.errorMessage.isEmpty() ? stats.errorMessage : "同步异常");
            views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFFEF4444);
        } else {
            views.setViewVisibility(R.id.tv_error_msg, View.GONE);
            views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFF10B981);
        }

        // 点击刷新事件
        Intent refreshIntent = new Intent(context, Sub2WidgetProvider.class);
        refreshIntent.setAction(Sub2WidgetProvider.ACTION_MANUAL_REFRESH);
        PendingIntent pRefresh = PendingIntent.getBroadcast(
            context, 20, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_refresh, pRefresh);

        // 点击设置事件
        Intent settingsIntent = new Intent(context, MainActivity.class);
        PendingIntent pSettings = PendingIntent.getActivity(
            context, 21, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_settings, pSettings);

        // 点击整个微件卡片直达后台
        String targetUrl = store.getServerUrl();
        if (!targetUrl.endsWith("/admin")) targetUrl += "/admin";
        Intent openWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
        openWeb.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pWeb = PendingIntent.getActivity(
            context, 22, openWeb, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, pWeb);

        mgr.updateAppWidget(id, views);
    }
}