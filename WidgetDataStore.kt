package top.ss2a.widget.data

import android.content.Context
import android.content.SharedPreferences

data class Sub2DashboardData(
    val todayCost: String = "$0.00",
    val todayRequests: Long = 0,
    val todayTokens: String = "0",
    val activeUsers: Int = 0,
    val activeKeys: Int = 0,
    val healthyAccounts: Int = 0,
    val totalAccounts: Int = 0,
    val lastUpdateTime: String = "--:--",
    val isSuccess: Boolean = true,
    val errorMessage: String = ""
)

class WidgetDataStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ss2a_widget_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", "https://ss2a.top") ?: "https://ss2a.top"
        set(value) = prefs.edit().putString("server_url", value.trimEnd('/')).apply()

    var adminToken: String
        get() = prefs.getString("admin_token", "") ?: ""
        set(value) = prefs.edit().putString("admin_token", value.trim()).apply()

    var adminAccount: String
        get() = prefs.getString("admin_account", "") ?: ""
        set(value) = prefs.edit().putString("admin_account", value.trim()).apply()

    var adminPassword: String
        get() = prefs.getString("admin_password", "") ?: ""
        set(value) = prefs.edit().putString("admin_password", value).apply()

    var refreshIntervalMinutes: Long
        get() = prefs.getLong("refresh_interval", 15L)
        set(value) = prefs.edit().putLong("refresh_interval", value).apply()

    // 缓存上一次获取的统计数据
    fun saveLatestStats(data: Sub2DashboardData) {
        prefs.edit()
            .putString("cache_cost", data.todayCost)
            .putLong("cache_requests", data.todayRequests)
            .putString("cache_tokens", data.todayTokens)
            .putInt("cache_users", data.activeUsers)
            .putInt("cache_keys", data.activeKeys)
            .putInt("cache_healthy_accounts", data.healthyAccounts)
            .putInt("cache_total_accounts", data.totalAccounts)
            .putString("cache_update_time", data.lastUpdateTime)
            .putBoolean("cache_success", data.isSuccess)
            .putString("cache_error", data.errorMessage)
            .apply()
    }

    fun getCachedStats(): Sub2DashboardData {
        return Sub2DashboardData(
            todayCost = prefs.getString("cache_cost", "$0.00") ?: "$0.00",
            todayRequests = prefs.getLong("cache_requests", 0),
            todayTokens = prefs.getString("cache_tokens", "0") ?: "0",
            activeUsers = prefs.getInt("cache_users", 0),
            activeKeys = prefs.getInt("cache_keys", 0),
            healthyAccounts = prefs.getInt("cache_healthy_accounts", 0),
            totalAccounts = prefs.getInt("cache_total_accounts", 0),
            lastUpdateTime = prefs.getString("cache_update_time", "--:--") ?: "--:--",
            isSuccess = prefs.getBoolean("cache_success", true),
            errorMessage = prefs.getString("cache_error", "") ?: ""
        )
    }
}