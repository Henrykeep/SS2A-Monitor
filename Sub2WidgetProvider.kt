package top.ss2a.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.ss2a.widget.R
import top.ss2a.widget.data.Sub2ApiClient
import top.ss2a.widget.data.Sub2DashboardData
import top.ss2a.widget.data.WidgetDataStore
import top.ss2a.widget.ui.MainActivity

class Sub2WidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_MANUAL_REFRESH = "top.ss2a.widget.ACTION_MANUAL_REFRESH"

        fun updateAllWidgets(context: Context, data: Sub2DashboardData) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, Sub2WidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                renderWidget(context, appWidgetManager, appWidgetId, data)
            }
        }

        private fun renderWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            data: Sub2DashboardData
        ) {
            val store = WidgetDataStore(context)
            val views = RemoteViews(context.packageName, R.layout.widget_sub2_4x2)

            // 1. 填充数据
            views.setTextViewText(R.id.tv_today_cost, data.todayCost)
            views.setTextViewText(R.id.tv_today_requests, "${data.todayRequests} 次")
            views.setTextViewText(R.id.tv_today_tokens, "${data.todayTokens} Tokens")
            views.setTextViewText(R.id.tv_update_time, "更新于 ${data.lastUpdateTime}")

            if (data.isSuccess) {
                views.setViewVisibility(R.id.tv_error_msg, View.GONE)
                views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFF10B981.toInt()) // 绿色正常
            } else {
                views.setViewVisibility(R.id.tv_error_msg, View.VISIBLE)
                views.setTextViewText(R.id.tv_error_msg, data.errorMessage.ifEmpty { "同步失败" })
                views.setInt(R.id.iv_status_dot, "setColorFilter", 0xFFEF4444.toInt()) // 红色错误
            }

            // 2. 点击右上角刷新按钮 -> 触发广播手动刷新
            val refreshIntent = Intent(context, Sub2WidgetProvider::class.java).apply {
                action = ACTION_MANUAL_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)

            // 3. 点击卡片主体 -> 打开 Sub2API 管理后台网页
            val openWebIntent = Intent(Intent.ACTION_VIEW).apply {
                val targetUrl = store.serverUrl.ifBlank { "https://ss2a.top" }
                data = Uri.parse(if (targetUrl.endsWith("/admin")) targetUrl else "$targetUrl/admin")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val openWebPendingIntent = PendingIntent.getActivity(
                context,
                1,
                openWebIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openWebPendingIntent)

            // 4. 点击设置齿轮小图标 -> 打开 App 配置页
            val settingsIntent = Intent(context, MainActivity::class.java)
            val settingsPendingIntent = PendingIntent.getActivity(
                context,
                2,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_settings, settingsPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 先用缓存秒开渲染
        val store = WidgetDataStore(context)
        val cached = store.getCachedStats()
        for (appWidgetId in appWidgetIds) {
            renderWidget(context, appWidgetManager, appWidgetId, cached)
        }

        // 后台异步请求最新数据刷新
        performAsyncFetch(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MANUAL_REFRESH) {
            // 即时反馈：先将更新时间状态切为 "正在同步..."
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, Sub2WidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            val loadingViews = RemoteViews(context.packageName, R.layout.widget_sub2_4x2)
            loadingViews.setTextViewText(R.id.tv_update_time, "正在同步...")
            appWidgetManager.partiallyUpdateAppWidget(appWidgetIds, loadingViews)

            performAsyncFetch(context)
        }
    }

    private fun performAsyncFetch(context: Context) {
        val appContext = context.applicationContext
        val store = WidgetDataStore(appContext)
        val apiClient = Sub2ApiClient(store)

        CoroutineScope(Dispatchers.IO).launch {
            val freshData = apiClient.fetchDashboardStats()
            updateAllWidgets(appContext, freshData)
        }
    }
}