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

        if (ACTION_MANUAL_REFRESH.equals(action) || "top.ss2a.widget.LOGS_MANUAL_REFRESH".equals(action)) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            ComponentName cn = new ComponentName(context, Sub2LogsWidgetProvider.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            if (ids != null && ids.length > 0) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_sub2_logs);
                views.setTextViewText(R.id.tv_update_time, "⚡ 同步中...");
                views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFF38BDF8); // 临时切换为闪电蓝
                mgr.partiallyUpdateAppWidget(ids, views);
            }
            Sub2RealtimeService.start(context);
            Sub2RealtimeService.syncNow(context);
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

        String updateTime = (stats != null && stats.lastUpdateTime != null) ? stats.lastUpdateTime : "--:--";
        views.setTextViewText(R.id.tv_update_time, "更新于 " + updateTime);

        // 顶栏当前活跃账户：100% 自动提取最近调用记录真实的真实上游渠道
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

        // 绑定底部汇总简报：加入吞吐速率与渠道活跃信息
        if (stats != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("今日: ").append(stats.todayCost).append(" (").append(stats.todayRequests).append("次)");
            if (stats.rpm > 0) {
                sb.append(" · ").append(stats.rpm).append(" RPM");
            }
            if (currentAccount != null && !currentAccount.trim().isEmpty()) {
                sb.append(" · ").append(currentAccount);
            }
            views.setTextViewText(R.id.tv_bottom_summary, sb.toString());
        }

        // 绑定 4 行最近调用记录，真实反映每笔调用的实际渠道
        int[] layoutIds = {R.id.layout_log_item_1, R.id.layout_log_item_2, R.id.layout_log_item_3, R.id.layout_log_item_4};
        int[] modelIds = {R.id.tv_log_model_1, R.id.tv_log_model_2, R.id.tv_log_model_3, R.id.tv_log_model_4};
        int[] accountIds = {R.id.tv_log_account_1, R.id.tv_log_account_2, R.id.tv_log_account_3, R.id.tv_log_account_4};
        int[] tokenIds = {R.id.tv_log_tokens_1, R.id.tv_log_tokens_2, R.id.tv_log_tokens_3, R.id.tv_log_tokens_4};
        int[] costIds = {R.id.tv_log_cost_1, R.id.tv_log_cost_2, R.id.tv_log_cost_3, R.id.tv_log_cost_4};
        int[] timeIds = {R.id.tv_log_time_1, R.id.tv_log_time_2, R.id.tv_log_time_3, R.id.tv_log_time_4};

        if (logs != null && !logs.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                if (i < logs.size()) {
                    Sub2LogItem item = logs.get(i);
                    views.setViewVisibility(layoutIds[i], View.VISIBLE);

                    // 1. 模型名优雅修剪 (去除前缀供应商和冗余版本号)
                    String mName = item.displayModel != null && !item.displayModel.isEmpty() ? item.displayModel : Sub2LogItem.cleanModelName(item.model);
                    views.setTextViewText(modelIds[i], mName);

                    views.setTextViewText(tokenIds[i], item.tokensSummary);
                    views.setTextViewText(costIds[i], item.cost);

                    // 2. 高额消费/普通消费差异化视觉提示 (金额 >= 0.05$ 呈琥珀珊瑚红，普通呈翡翠绿)
                    if (item.rawCost >= 0.05) {
                        views.setTextColor(costIds[i], 0xFFFB7185); // 琥珀珊瑚红
                    } else {
                        views.setTextColor(costIds[i], 0xFF34D399); // 经典翡翠绿
                    }

                    views.setTextViewText(timeIds[i], item.time);

                    // 3. 真实上游渠道徽章标签
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
            // 优雅的待机状态微看板
            views.setTextViewText(modelIds[0], "🟢 服务待命中 · 监听新请求");
            views.setViewVisibility(accountIds[0], View.GONE);
            views.setTextViewText(tokenIds[0], stats != null ? (stats.rpm + " RPM") : "0 RPM");
            views.setTextViewText(costIds[0], "$0.00");
            views.setTextColor(costIds[0], 0xFF34D399);
            views.setTextViewText(timeIds[0], updateTime);
            views.setViewVisibility(layoutIds[0], View.VISIBLE);
            views.setViewVisibility(layoutIds[1], View.GONE);
            views.setViewVisibility(layoutIds[2], View.GONE);
            views.setViewVisibility(layoutIds[3], View.GONE);
        }

        // 成功状态或异常条指示灯
        if (stats != null && !stats.isSuccess) {
            views.setViewVisibility(R.id.tv_error_msg, View.VISIBLE);
            views.setTextViewText(R.id.tv_error_msg, stats.errorMessage != null && !stats.errorMessage.isEmpty() ? stats.errorMessage : "同步异常");
            views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFFEF4444); // 异常亮红灯
        } else {
            views.setViewVisibility(R.id.tv_error_msg, View.GONE);
            views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFF10B981); // 正常翡翠绿
        }

        // ==================== 交互直达优化 ====================
        String baseUrl = Sub2ApiClient.cleanBaseUrl(store.getServerUrl());

        // 1. 点击刷新按钮
        Intent refreshIntent = new Intent(context, Sub2WidgetProvider.class);
        refreshIntent.setAction(Sub2WidgetProvider.ACTION_MANUAL_REFRESH);
        PendingIntent pRefresh = PendingIntent.getBroadcast(
            context, 20, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_refresh, pRefresh);

        // 2. 点击设置按钮
        Intent settingsIntent = new Intent(context, MainActivity.class);
        PendingIntent pSettings = PendingIntent.getActivity(
            context, 21, settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_settings, pSettings);

        // 3. 点击顶部账户胶囊：直达中转后台「渠道与账号管理 (/admin/accounts)」
        Intent openAccounts = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + "/admin/accounts"));
        openAccounts.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pAccounts = PendingIntent.getActivity(
            context, 23, openAccounts, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tv_top_account, pAccounts);

        // 4. 点击 4 条调用明细行：直达中转后台「调用日志与使用记录 (/admin/logs)」
        Intent openLogs = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + "/admin/logs"));
        openLogs.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pLogs = PendingIntent.getActivity(
            context, 24, openLogs, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int lId : layoutIds) {
            views.setOnClickPendingIntent(lId, pLogs);
        }

        // 5. 点击底部汇总简报及主体底板：直达中转站首页/控制台 (/admin)
        Intent openWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(baseUrl + "/admin"));
        openWeb.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pWeb = PendingIntent.getActivity(
            context, 22, openWeb, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tv_bottom_summary, pWeb);
        views.setOnClickPendingIntent(R.id.widget_container, pWeb);

        mgr.updateAppWidget(id, views);
    }
}
