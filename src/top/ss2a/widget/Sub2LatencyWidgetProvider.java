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

public class Sub2LatencyWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_MANUAL_REFRESH = "top.ss2a.widget.LATENCY_MANUAL_REFRESH";

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

        if (Sub2WidgetProvider.ACTION_MANUAL_REFRESH.equals(action) 
                || ACTION_MANUAL_REFRESH.equals(action)
                || "top.ss2a.widget.LOGS_MANUAL_REFRESH".equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, Sub2LatencyWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids != null && ids.length > 0) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sub2_latency);
                views.setTextViewText(R.id.tv_update_time, "⚡ 同步中...");
                views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFF38BDF8);
                mgr.partiallyUpdateAppWidget(ids, views);
            }
            Sub2RealtimeService.start(context);
            Sub2RealtimeService.syncNow(context);
        }
    }

    public static void updateAllWidgets(Context context, Sub2DashboardData stats, List<Sub2LogItem> logs) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, Sub2LatencyWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids == null || ids.length == 0) return;

        for (int id : ids) {
            renderWidget(context, mgr, id, stats, logs);
        }
    }

    private static void renderWidget(Context context, AppWidgetManager mgr, int id, Sub2DashboardData stats, List<Sub2LogItem> logs) {
        WidgetDataStore store = new WidgetDataStore(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sub2_latency);

        String updateTime = (stats != null && stats.lastUpdateTime != null) ? stats.lastUpdateTime : "--:--";
        views.setTextViewText(R.id.tv_update_time, "更新于 " + updateTime);

        // 顶栏当前活跃账户：自动提取最近调用记录真实的真实上游渠道
        String currentAccount = "";
        if (logs != null && !logs.isEmpty()) {
            for (Sub2LogItem it : logs) {
                if (it.account != null && !it.account.trim().isEmpty()) {
                    currentAccount = it.account.trim();
                    break;
                }
            }
        }
        if (currentAccount != null && !currentAccount.trim().isEmpty()) {
            views.setViewVisibility(R.id.tv_top_account, View.VISIBLE);
            views.setTextViewText(R.id.tv_top_account, currentAccount);
        } else {
            views.setViewVisibility(R.id.tv_top_account, View.GONE);
        }

        // 绑定底部汇总简报：平均耗时与吞吐速率
        if (stats != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("平均耗时: ").append(stats.avgDuration != null ? stats.avgDuration : "0s");
            if (stats.rpm > 0) {
                sb.append(" · ").append(stats.rpm).append(" RPM");
            }
            if (stats.todayRequests > 0) {
                sb.append(" · 今日 ").append(stats.todayRequests).append("次");
            }
            views.setTextViewText(R.id.tv_bottom_summary, sb.toString());
        }

        // 绑定 4 行最近调用记录，真实反映首字延迟与总耗时
        int[] layoutIds = {R.id.layout_log_item_1, R.id.layout_log_item_2, R.id.layout_log_item_3, R.id.layout_log_item_4};
        int[] modelIds = {R.id.tv_log_model_1, R.id.tv_log_model_2, R.id.tv_log_model_3, R.id.tv_log_model_4};
        int[] accountIds = {R.id.tv_log_account_1, R.id.tv_log_account_2, R.id.tv_log_account_3, R.id.tv_log_account_4};
        int[] firstTokenIds = {R.id.tv_log_first_token_1, R.id.tv_log_first_token_2, R.id.tv_log_first_token_3, R.id.tv_log_first_token_4};
        int[] durationIds = {R.id.tv_log_duration_1, R.id.tv_log_duration_2, R.id.tv_log_duration_3, R.id.tv_log_duration_4};
        int[] timeIds = {R.id.tv_log_time_1, R.id.tv_log_time_2, R.id.tv_log_time_3, R.id.tv_log_time_4};

        if (logs != null && !logs.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                if (i < logs.size()) {
                    Sub2LogItem item = logs.get(i);
                    views.setViewVisibility(layoutIds[i], View.VISIBLE);

                    // 1. 模型名舒展完整显示
                    String mName = item.displayModel != null && !item.displayModel.isEmpty() ? item.displayModel : Sub2LogItem.cleanModelName(item.model);
                    views.setTextViewText(modelIds[i], mName);

                    // 2. 首字延迟 (天蓝色)
                    String ftStr = (item.firstToken != null && !item.firstToken.isEmpty()) ? item.firstToken : (item.firstTokenMs > 0 ? (item.firstTokenMs + "ms") : "--");
                    views.setTextViewText(firstTokenIds[i], ftStr);

                    // 3. 总耗时：>= 10s 警告珊瑚红，普通翡翠绿
                    String durStr = (item.duration != null && !item.duration.isEmpty()) ? item.duration : (item.durationMs > 0 ? (item.durationMs + "ms") : "--");
                    views.setTextViewText(durationIds[i], durStr);
                    if (item.durationMs >= 10000) {
                        views.setTextColor(durationIds[i], 0xFFFB7185); // 珊瑚红警告
                    } else {
                        views.setTextColor(durationIds[i], 0xFF34D399); // 翡翠绿
                    }

                    // 4. 秒级时间戳
                    views.setTextViewText(timeIds[i], item.time);

                    // 5. 真实渠道标签
                    String itemAccount = item.account != null ? item.account.trim() : "";
                    if (itemAccount != null && !itemAccount.isEmpty()) {
                        views.setViewVisibility(accountIds[i], View.VISIBLE);
                        views.setTextViewText(accountIds[i], itemAccount);
                    } else {
                        views.setViewVisibility(accountIds[i], View.GONE);
                    }
                } else {
                    views.setViewVisibility(layoutIds[i], View.GONE);
                }
            }
        } else {
            views.setTextViewText(modelIds[0], "🟢 性能监控待命中");
            views.setViewVisibility(accountIds[0], View.GONE);
            views.setTextViewText(firstTokenIds[0], "--");
            views.setTextViewText(durationIds[0], "--");
            views.setTextViewText(timeIds[0], updateTime);
            views.setViewVisibility(layoutIds[0], View.VISIBLE);
            views.setViewVisibility(layoutIds[1], View.GONE);
            views.setViewVisibility(layoutIds[2], View.GONE);
            views.setViewVisibility(layoutIds[3], View.GONE);
        }

        // 成功状态或异常条指示灯
        if (stats != null && !stats.isSuccess) {
            views.setViewVisibility(R.id.tv_error_msg, View.VISIBLE);
            views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFFEF4444);
        } else {
            views.setViewVisibility(R.id.tv_error_msg, View.GONE);
            views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFF10B981);
        }

        // 点击交互跳转
        String baseUrl = Sub2ApiClient.cleanBaseUrl(store.getServerUrl());

        Intent refreshIntent = new Intent(context, Sub2LatencyWidgetProvider.class);
        refreshIntent.setAction(ACTION_MANUAL_REFRESH);
        PendingIntent pRefresh = PendingIntent.getBroadcast(
            context, 30, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_refresh, pRefresh);

        Intent settingsIntent = new Intent(context, MainActivity.class);
        PendingIntent pSettings = PendingIntent.getActivity(
            context, 31, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_settings, pSettings);

        Intent openAccounts = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + "/admin/accounts"));
        openAccounts.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pAccounts = PendingIntent.getActivity(
            context, 33, openAccounts, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tv_top_account, pAccounts);

        Intent openLogs = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + "/admin/logs"));
        openLogs.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pLogs = PendingIntent.getActivity(
            context, 34, openLogs, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int lId : layoutIds) {
            views.setOnClickPendingIntent(lId, pLogs);
        }

        Intent openWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + "/admin"));
        openWeb.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pWeb = PendingIntent.getActivity(
            context, 32, openWeb, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tv_bottom_summary, pWeb);
        views.setOnClickPendingIntent(R.id.widget_container, pWeb);

        mgr.updateAppWidget(id, views);
    }
}