package top.ss2a.widget;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Sub2ApiClient {
    private final WidgetDataStore dataStore;

    public Sub2ApiClient(WidgetDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public static String cleanBaseUrl(String input) {
        if (input == null || input.trim().isEmpty()) return "https://ss2a.top";
        String url = input.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/api/v1")) {
            url = url.substring(0, url.length() - "/api/v1".length());
        } else if (url.endsWith("/admin")) {
            url = url.substring(0, url.length() - "/admin".length());
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }

    public String loginAdmin(String account, String password) throws Exception {
        String baseUrl = cleanBaseUrl(dataStore.getServerUrl());
        String loginUrl = baseUrl + "/api/v1/auth/login";

        JSONObject payload = new JSONObject();
        String acc = account.trim();
        payload.put("email", acc);
        payload.put("username", acc);
        payload.put("password", password);
        byte[] postBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(loginUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postBytes);
                os.flush();
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String body = readStream(is);

            if (code >= 200 && code < 300) {
                String token = extractToken(body);
                if (token != null && !token.isEmpty()) {
                    dataStore.setAdminAccount(account.trim());
                    dataStore.setAdminPassword(password);
                    dataStore.setAdminToken(token);
                    return token;
                } else {
                    throw new Exception("登录成功但未提取到 Token: " + body);
                }
            } else {
                String msg = parseErrorMsg(body, code);
                throw new Exception(msg);
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public Sub2DashboardData fetchDashboardStats(boolean allowRelogin) {
        String baseUrl = cleanBaseUrl(dataStore.getServerUrl());
        String token = dataStore.getAdminToken();

        // 若无 Token 但有账号密码，先尝试登录
        if ((token == null || token.isEmpty()) && !dataStore.getAdminAccount().isEmpty() && !dataStore.getAdminPassword().isEmpty()) {
            try {
                token = loginAdmin(dataStore.getAdminAccount(), dataStore.getAdminPassword());
            } catch (Exception ignored) {}
        }

        if (token == null || token.isEmpty()) {
            Sub2DashboardData err = new Sub2DashboardData();
            err.isSuccess = false;
            err.errorMessage = "未配置 Token，请先登录或手动填入";
            return err;
        }

        String timeStr = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        // 1. 优先尝试管理员全站接口
        Sub2DashboardData adminData = requestStatsEndpoint(baseUrl + "/api/v1/admin/dashboard/stats", token, timeStr, true);
        if (adminData.isSuccess) {
            adminData.modeTitle = "全站监控";
            dataStore.saveLatestStats(adminData);
            return adminData;
        }

        // 2. 如果是 401 且允许重登，重登一次后再试
        if ("UNAUTHORIZED".equals(adminData.errorMessage) && allowRelogin) {
            if (!dataStore.getAdminAccount().isEmpty() && !dataStore.getAdminPassword().isEmpty()) {
                try {
                    token = loginAdmin(dataStore.getAdminAccount(), dataStore.getAdminPassword());
                    return fetchDashboardStats(false);
                } catch (Exception ignored) {}
            }
        }

        // 3. 如果报 403 (Admin access required) 或 404，说明此账号是普通用户或系统部署为非admin路由，自动优雅降级拉取个人仪表盘！
        boolean isForbidden = adminData.errorMessage != null && (
            adminData.errorMessage.contains("403") ||
            adminData.errorMessage.contains("404") ||
            adminData.errorMessage.toLowerCase().contains("admin access") ||
            adminData.errorMessage.toLowerCase().contains("forbidden")
        );

        if (isForbidden) {
            Sub2DashboardData userData = requestStatsEndpoint(baseUrl + "/api/v1/usage/dashboard/stats", token, timeStr, false);
            if (userData.isSuccess) {
                userData.modeTitle = "个人用量";
                dataStore.saveLatestStats(userData);
                return userData;
            }

            // 备用个人用量接口
            Sub2DashboardData fallbackUser = requestStatsEndpoint(baseUrl + "/api/v1/usage/stats", token, timeStr, false);
            if (fallbackUser.isSuccess) {
                fallbackUser.modeTitle = "个人用量";
                dataStore.saveLatestStats(fallbackUser);
                return fallbackUser;
            }
        }

        // 4. 若最终均未成功，保留旧缓存，标记错误
        Sub2DashboardData cached = dataStore.getCachedStats();
        cached.isSuccess = false;
        cached.errorMessage = adminData.errorMessage;
        return cached;
    }

    private Sub2DashboardData requestStatsEndpoint(String endpointUrl, String token, String timeStr, boolean isAdminEndpoint) {
        Sub2DashboardData data = new Sub2DashboardData();
        data.lastUpdateTime = timeStr;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpointUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");

            String rawToken = token.trim();
            if (rawToken.startsWith("Bearer ")) {
                rawToken = rawToken.substring(7).trim();
            }

            // 智能兼容多种认证头格式：JWT Token、自定义 x-api-key 以及标准 Bearer
            boolean isJwt = rawToken.contains(".") && rawToken.split("\\.").length >= 3;
            conn.setRequestProperty("Authorization", "Bearer " + rawToken);
            if (!isJwt) {
                conn.setRequestProperty("x-api-key", rawToken);
                conn.setRequestProperty("apikey", rawToken);
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                String body = readStream(conn.getInputStream());
                data = parseStatsJson(body, timeStr);
                data.isSuccess = true;
                return data;
            } else {
                if (code == 401) {
                    data.isSuccess = false;
                    data.errorMessage = "UNAUTHORIZED";
                    return data;
                }
                InputStream errIs = conn.getErrorStream();
                String errBody = readStream(errIs);
                String msg = parseErrorMsg(errBody, code);
                data.isSuccess = false;
                data.errorMessage = msg;
                return data;
            }
        } catch (Exception e) {
            data.isSuccess = false;
            data.errorMessage = e.getMessage() != null ? e.getMessage() : "网络请求失败";
            return data;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String extractToken(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject dataObj = root.optJSONObject("data");
            if (dataObj == null) dataObj = root;
            String t = dataObj.optString("access_token", "");
            if (!t.isEmpty()) return t;
            t = dataObj.optString("token", "");
            if (!t.isEmpty()) return t;
            t = dataObj.optString("jwt", "");
            if (!t.isEmpty()) return t;
            return root.optString("token", "");
        } catch (Exception e) {
            return null;
        }
    }

    private String parseErrorMsg(String body, int code) {
        if (body != null && !body.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(body);
                if (obj.has("message")) {
                    return obj.getString("message");
                }
                if (obj.has("error")) {
                    return obj.getString("error");
                }
            } catch (Exception ignored) {}
        }
        if (code == 401) return "认证失败(401)：Token无效或已过期";
        if (code == 403) return "权限不足(403)：Admin access required";
        if (code == 404) return "接口不存在(404)：请检查服务器地址";
        return "HTTP " + code;
    }

    private Sub2DashboardData parseStatsJson(String jsonStr, String timeStr) {
        Sub2DashboardData data = new Sub2DashboardData();
        data.lastUpdateTime = timeStr;
        data.isSuccess = true;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject target = root.optJSONObject("data");
            if (target == null) target = root.optJSONObject("result");
            if (target == null) target = root;

            // 1. 今日用量 (Today Stats)
            double todayCostVal = optDouble(target, new String[]{"today_actual_cost", "today_cost", "today_amount", "daily_cost"});
            data.todayCost = "$" + new DecimalFormat("0.00").format(todayCostVal);

            data.todayRequests = optLong(target, new String[]{"today_requests", "today_request_count"});
            long todayTokensVal = optLong(target, new String[]{"today_tokens"});
            data.todayTokens = formatTokens(todayTokensVal);

            long todayInTokens = optLong(target, new String[]{"today_input_tokens"});
            data.todayInputTokens = formatTokens(todayInTokens);

            long todayOutTokens = optLong(target, new String[]{"today_output_tokens"});
            data.todayOutputTokens = formatTokens(todayOutTokens);

            long todayCacheTokens = optLong(target, new String[]{"today_cache_read_tokens"});
            data.todayCacheReadTokens = formatTokens(todayCacheTokens);

            // 2. 累计总用量 (Total / All-time Stats)
            double totalCostVal = optDouble(target, new String[]{"total_actual_cost", "total_cost", "cost"});
            if (totalCostVal == 0.0 && todayCostVal > 0.0) {
                totalCostVal = todayCostVal;
            }
            data.totalCost = "$" + new DecimalFormat("0.00").format(totalCostVal);

            data.totalRequests = optLong(target, new String[]{"total_requests", "requests"});
            if (data.totalRequests == 0 && data.todayRequests > 0) {
                data.totalRequests = data.todayRequests;
            }

            long totalTokensVal = optLong(target, new String[]{"total_tokens", "tokens"});
            if (totalTokensVal == 0 && todayTokensVal > 0) {
                totalTokensVal = todayTokensVal;
            }
            data.totalTokens = formatTokens(totalTokensVal);

            long totalInTokens = optLong(target, new String[]{"total_input_tokens"});
            data.totalInputTokens = formatTokens(totalInTokens);

            long totalOutTokens = optLong(target, new String[]{"total_output_tokens"});
            data.totalOutputTokens = formatTokens(totalOutTokens);

            long totalCacheTokens = optLong(target, new String[]{"total_cache_read_tokens"});
            data.totalCacheReadTokens = formatTokens(totalCacheTokens);

            // 3. 实时负载与性能
            data.rpm = optInt(target, new String[]{"rpm"});
            long tpmVal = optLong(target, new String[]{"tpm"});
            data.tpm = formatTokens(tpmVal);

            double durationMs = optDouble(target, new String[]{"average_duration_ms", "duration_ms", "avg_latency"});
            data.avgDuration = formatDuration(durationMs);

            // 4. API 密钥与用户
            data.totalKeys = optInt(target, new String[]{"total_api_keys", "key_count", "total_keys"});
            data.activeKeys = optInt(target, new String[]{"active_api_keys", "active_keys"});
            data.activeUsers = optInt(target, new String[]{"active_users", "user_count", "total_users", "today_new_users"});
        } catch (Exception e) {
            data.isSuccess = false;
            data.errorMessage = "数据解析异常";
        }
        return data;
    }

    private double optDouble(JSONObject obj, String[] keys) {
        for (String k : keys) {
            if (obj.has(k)) return obj.optDouble(k, 0.0);
        }
        return 0.0;
    }

    private long optLong(JSONObject obj, String[] keys) {
        for (String k : keys) {
            if (obj.has(k)) return obj.optLong(k, 0L);
        }
        return 0L;
    }

    private int optInt(JSONObject obj, String[] keys) {
        for (String k : keys) {
            if (obj.has(k)) return obj.optInt(k, 0);
        }
        return 0;
    }

    private String formatTokens(long t) {
        if (t >= 1000000000L) return String.format(Locale.US, "%.2fB", t / 1000000000.0);
        if (t >= 1000000L) return String.format(Locale.US, "%.2fM", t / 1000000.0);
        if (t >= 1000L) return String.format(Locale.US, "%.1fK", t / 1000.0);
        return String.valueOf(t);
    }

    public List<Sub2LogItem> fetchRecentLogs(int limit) {
        List<Sub2LogItem> list = new ArrayList<>();
        String baseUrl = cleanBaseUrl(dataStore.getServerUrl());
        String token = dataStore.getAdminToken();
        if (token == null || token.isEmpty()) return list;

        String endpointUrl = baseUrl + "/api/v1/usage?page=1&page_size=" + Math.max(limit, 5);
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpointUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");

            String rawToken = token.trim();
            if (rawToken.startsWith("Bearer ")) {
                rawToken = rawToken.substring(7).trim();
            }
            conn.setRequestProperty("Authorization", "Bearer " + rawToken);

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                String body = readStream(conn.getInputStream());
                JSONObject root = new JSONObject(body);
                JSONObject dataObj = root.optJSONObject("data");
                JSONArray items = dataObj != null ? dataObj.optJSONArray("items") : null;
                if (items == null && root.has("items")) items = root.optJSONArray("items");

                if (items != null) {
                    for (int i = 0; i < items.length() && list.size() < limit; i++) {
                        JSONObject itemObj = items.optJSONObject(i);
                        if (itemObj == null) continue;

                        long id = itemObj.optLong("id", 0);
                        String model = itemObj.optString("model", "未知模型");
                        String createdAt = itemObj.optString("created_at", "");
                        double cost = itemObj.optDouble("actual_cost", itemObj.optDouble("total_cost", 0.0));
                        long durationMs = itemObj.optLong("duration_ms", 0);
                        long firstTokenMs = itemObj.optLong("first_token_ms", 0);
                        String firstTokenStr = firstTokenMs > 0 ? formatDuration(firstTokenMs) : "--";
                        long inTokens = itemObj.optLong("input_tokens", 0);
                        long outTokens = itemObj.optLong("output_tokens", 0);
                        long cacheTokens = itemObj.optLong("cache_read_tokens", 0);

                        String timeStr = formatLogTime(createdAt);
                        String costStr = "$" + new DecimalFormat("0.0000").format(cost);
                        String durationStr = formatDuration(durationMs);
                        String tokensStr = formatTokens(inTokens + outTokens + cacheTokens);

                        // 忠实提取真实上游渠道账户 (如 gugugaga, K 等)，严格反映客观真实数据，绝不捏造
                        String accountStr = "";
                        if (itemObj.has("account_name") && !itemObj.optString("account_name").trim().isEmpty()) {
                            accountStr = itemObj.optString("account_name").trim();
                        } else if (itemObj.has("channel_name") && !itemObj.optString("channel_name").trim().isEmpty()) {
                            accountStr = itemObj.optString("channel_name").trim();
                        } else if (itemObj.has("account")) {
                            JSONObject accObj = itemObj.optJSONObject("account");
                            if (accObj != null) {
                                accountStr = accObj.optString("name", accObj.optString("title", "")).trim();
                            } else {
                                accountStr = itemObj.optString("account", "").trim();
                            }
                        } else if (itemObj.has("channel")) {
                            JSONObject chObj = itemObj.optJSONObject("channel");
                            if (chObj != null) {
                                accountStr = chObj.optString("name", "").trim();
                            } else {
                                accountStr = itemObj.optString("channel", "").trim();
                            }
                        } else if (itemObj.has("upstream_name")) {
                            accountStr = itemObj.optString("upstream_name", "").trim();
                        }

                        Sub2LogItem logItem = new Sub2LogItem(id, model, timeStr, costStr, durationStr, tokensStr);
                        logItem.account = accountStr;
                        logItem.rawCost = cost;
                        logItem.durationMs = durationMs;
                        logItem.firstTokenMs = firstTokenMs;
                        logItem.firstToken = firstTokenStr;
                        logItem.hasCacheHit = (cacheTokens > 0);
                        list.add(logItem);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return list;
    }

    private String formatLogTime(String isoTime) {
        if (isoTime == null || isoTime.trim().isEmpty()) return "--:--";
        try {
            // 兼容 ISO 格式: 2026-10-05T19:49:41.625551+08:00 或带空格格式 2026-10-05 19:49:41
            int tIndex = isoTime.indexOf('T');
            if (tIndex < 0) {
                tIndex = isoTime.indexOf(' ');
            }
            if (tIndex >= 0 && isoTime.length() >= tIndex + 9) {
                return isoTime.substring(tIndex + 1, tIndex + 9);
            }
        } catch (Exception ignored) {}
        return isoTime;
    }

    private String formatDuration(double ms) {
        if (ms <= 0) return "0.0s";
        if (ms >= 1000.0) {
            return String.format(Locale.US, "%.1fs", ms / 1000.0);
        }
        // 小于1秒同样采用秒级统一展现 (如 0.8s, 0.3s)，保证全列单位"s"与小数点物理绝对对齐！
        return String.format(Locale.US, "%.1fs", ms / 1000.0);
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}