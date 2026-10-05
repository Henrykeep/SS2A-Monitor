package top.ss2a.widget;

import android.content.Context;
import android.content.SharedPreferences;

public class WidgetDataStore {
    private final SharedPreferences prefs;

    public WidgetDataStore(Context context) {
        prefs = context.getSharedPreferences("ss2a_widget_prefs", Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return prefs.getString("server_url", "https://ss2a.top");
    }

    public void setServerUrl(String url) {
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        prefs.edit().putString("server_url", url).apply();
    }

    public String getAdminToken() {
        return prefs.getString("admin_token", "");
    }

    public void setAdminToken(String token) {
        prefs.edit().putString("admin_token", token != null ? token.trim() : "").apply();
    }

    public String getAdminAccount() {
        return prefs.getString("admin_account", "");
    }

    public void setAdminAccount(String account) {
        prefs.edit().putString("admin_account", account != null ? account.trim() : "").apply();
    }

    public String getAdminPassword() {
        return prefs.getString("admin_password", "");
    }

    public void setAdminPassword(String password) {
        prefs.edit().putString("admin_password", password != null ? password : "").apply();
    }

    public int getRefreshIntervalSeconds() {
        return prefs.getInt("refresh_interval", 30);
    }

    public void setRefreshIntervalSeconds(int sec) {
        if (sec < 5) sec = 5;
        prefs.edit().putInt("refresh_interval", sec).apply();
    }

    public void saveLatestStats(Sub2DashboardData data) {
        prefs.edit()
            .putString("cache_mode_title", data.modeTitle)
            .putString("cache_cost", data.todayCost)
            .putLong("cache_requests", data.todayRequests)
            .putString("cache_tokens", data.todayTokens)
            .putInt("cache_users", data.activeUsers)
            .putInt("cache_keys", data.activeKeys)
            .putString("cache_update_time", data.lastUpdateTime)
            .putBoolean("cache_success", data.isSuccess)
            .putString("cache_error", data.errorMessage)
            .apply();
    }

    public Sub2DashboardData getCachedStats() {
        Sub2DashboardData data = new Sub2DashboardData();
        data.modeTitle = prefs.getString("cache_mode_title", "全站监控");
        data.todayCost = prefs.getString("cache_cost", "$0.00");
        data.todayRequests = prefs.getLong("cache_requests", 0);
        data.todayTokens = prefs.getString("cache_tokens", "0");
        data.activeUsers = prefs.getInt("cache_users", 0);
        data.activeKeys = prefs.getInt("cache_keys", 0);
        data.lastUpdateTime = prefs.getString("cache_update_time", "--:--");
        data.isSuccess = prefs.getBoolean("cache_success", true);
        data.errorMessage = prefs.getString("cache_error", "");
        return data;
    }
}
