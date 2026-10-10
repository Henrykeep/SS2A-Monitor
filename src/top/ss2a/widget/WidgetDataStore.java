package top.ss2a.widget;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

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
    public String getTurnstileSiteKey() {
        return prefs.getString("turnstile_site_key", "");
    }
    public void setTurnstileSiteKey(String key) {
        prefs.edit().putString("turnstile_site_key", key != null ? key.trim() : "").apply();
    }
    public String getTurnstileToken() {
        return prefs.getString("turnstile_token", "");
    }
    public void setTurnstileToken(String token) {
        prefs.edit().putString("turnstile_token", token != null ? token.trim() : "").apply();
    }

    // 真正展示的上游账户名，例如 gugugaga, K 等
    public String getTargetAccountName() {
        return prefs.getString("target_account_name", "gugugaga");
    }

    public void setTargetAccountName(String name) {
        prefs.edit().putString("target_account_name", name != null ? name.trim() : "").apply();
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
            .putString("cache_today_input_tokens", data.todayInputTokens)
            .putString("cache_today_output_tokens", data.todayOutputTokens)
            .putString("cache_today_cache_tokens", data.todayCacheReadTokens)
            .putString("cache_total_cost", data.totalCost)
            .putLong("cache_total_requests", data.totalRequests)
            .putString("cache_total_tokens", data.totalTokens)
            .putString("cache_total_input_tokens", data.totalInputTokens)
            .putString("cache_total_output_tokens", data.totalOutputTokens)
            .putString("cache_total_cache_tokens", data.totalCacheReadTokens)
            .putInt("cache_rpm", data.rpm)
            .putString("cache_tpm", data.tpm)
            .putString("cache_avg_duration", data.avgDuration)
            .putInt("cache_total_keys", data.totalKeys)
            .putInt("cache_users", data.activeUsers)
            .putInt("cache_keys", data.activeKeys)
            .putString("cache_update_time", data.lastUpdateTime)
            .putBoolean("cache_success", data.isSuccess)
            .putString("cache_error", data.errorMessage)
            .apply();
    }

    public void saveRecentLogs(List<Sub2LogItem> logs) {
        JSONArray array = new JSONArray();
        if (logs != null) {
            for (Sub2LogItem item : logs) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("id", item.id);
                    obj.put("model", item.model);
                    obj.put("time", item.time);
                    obj.put("cost", item.cost);
                    obj.put("duration", item.duration);
                    obj.put("tokens", item.tokensSummary);
                    obj.put("account", item.account != null ? item.account : "");
                    obj.put("display_model", item.displayModel != null ? item.displayModel : item.model);
                    obj.put("raw_cost", item.rawCost);
                    obj.put("duration_ms", item.durationMs);
                    obj.put("first_token_ms", item.firstTokenMs);
                    obj.put("first_token", item.firstToken != null ? item.firstToken : "0s");
                    obj.put("cache_hit", item.hasCacheHit);
                    array.put(obj);
                } catch (Exception ignored) {}
            }
        }
        prefs.edit().putString("cache_recent_logs", array.toString()).apply();
    }

    public Sub2DashboardData getCachedStats() {
        Sub2DashboardData data = new Sub2DashboardData();
        data.modeTitle = prefs.getString("cache_mode_title", "全站监控");
        data.todayCost = prefs.getString("cache_cost", "$0.00");
        data.todayRequests = prefs.getLong("cache_requests", 0);
        data.todayTokens = prefs.getString("cache_tokens", "0");
        data.todayInputTokens = prefs.getString("cache_today_input_tokens", "0");
        data.todayOutputTokens = prefs.getString("cache_today_output_tokens", "0");
        data.todayCacheReadTokens = prefs.getString("cache_today_cache_tokens", "0");
        data.totalCost = prefs.getString("cache_total_cost", "$0.00");
        data.totalRequests = prefs.getLong("cache_total_requests", 0);
        data.totalTokens = prefs.getString("cache_total_tokens", "0");
        data.totalInputTokens = prefs.getString("cache_total_input_tokens", "0");
        data.totalOutputTokens = prefs.getString("cache_total_output_tokens", "0");
        data.totalCacheReadTokens = prefs.getString("cache_total_cache_tokens", "0");
        data.rpm = prefs.getInt("cache_rpm", 0);
        data.tpm = prefs.getString("cache_tpm", "0");
        data.avgDuration = prefs.getString("cache_avg_duration", "0s");
        data.totalKeys = prefs.getInt("cache_total_keys", 0);
        data.activeUsers = prefs.getInt("cache_users", 0);
        data.activeKeys = prefs.getInt("cache_keys", 0);
        data.lastUpdateTime = prefs.getString("cache_update_time", "--:--");
        data.isSuccess = prefs.getBoolean("cache_success", true);
        data.errorMessage = prefs.getString("cache_error", "");
        return data;
    }

    public List<Sub2LogItem> getCachedRecentLogs() {
        List<Sub2LogItem> list = new ArrayList<>();
        String json = prefs.getString("cache_recent_logs", "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Sub2LogItem item = new Sub2LogItem();
                item.id = obj.optLong("id", 0);
                item.model = obj.optString("model", "未知模型");
                item.time = obj.optString("time", "--:--");
                item.cost = obj.optString("cost", "$0.00");
                item.duration = obj.optString("duration", "0s");
                item.tokensSummary = obj.optString("tokens", "0 Tokens");
                item.account = obj.optString("account", "");
                item.displayModel = obj.optString("display_model", Sub2LogItem.cleanModelName(item.model));
                item.rawCost = obj.optDouble("raw_cost", 0.0);
                item.durationMs = obj.optLong("duration_ms", 0);
                item.firstTokenMs = obj.optLong("first_token_ms", 0);
                item.firstToken = obj.optString("first_token", "0s");
                item.hasCacheHit = obj.optBoolean("cache_hit", false);
                list.add(item);
            }
        } catch (Exception ignored) {}
        return list;
    }
}
