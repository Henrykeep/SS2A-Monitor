package top.ss2a.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class Sub2Router {

    /**
     * 判断当前数据或缓存是否为管理员全站监控模式
     */
    public static boolean isAdminMode(Sub2DashboardData data, WidgetDataStore store) {
        if (data != null && data.modeTitle != null && !data.modeTitle.trim().isEmpty()) {
            return "全站监控".equals(data.modeTitle.trim());
        }
        if (store != null) {
            Sub2DashboardData cached = store.getCachedStats();
            if (cached != null && cached.modeTitle != null && !cached.modeTitle.trim().isEmpty()) {
                return "全站监控".equals(cached.modeTitle.trim());
            }
        }
        return false;
    }

    /**
     * 1. 控制台/概览：管理员 -> /admin, 普通用户 -> /dashboard
     */
    public static String getDashboardUrl(String baseUrl, boolean isAdmin) {
        String base = Sub2ApiClient.cleanBaseUrl(baseUrl);
        return isAdmin ? base + "/admin" : base + "/dashboard";
    }

    /**
     * 2. 调用明细与使用日志：管理员 -> /admin/usage, 普通用户 -> /usage
     */
    public static String getUsageUrl(String baseUrl, boolean isAdmin) {
        String base = Sub2ApiClient.cleanBaseUrl(baseUrl);
        return isAdmin ? base + "/admin/usage" : base + "/usage";
    }

    /**
     * 3. 上游渠道与账号管理：管理员 -> /admin/accounts, 普通用户 -> /my-accounts
     */
    public static String getAccountsUrl(String baseUrl, boolean isAdmin) {
        String base = Sub2ApiClient.cleanBaseUrl(baseUrl);
        return isAdmin ? base + "/admin/accounts" : base + "/my-accounts";
    }

    /**
     * 4. 密钥管理：管理员与普通用户统一 -> /keys
     */
    public static String getKeysUrl(String baseUrl) {
        String base = Sub2ApiClient.cleanBaseUrl(baseUrl);
        return base + "/keys";
    }

    /**
     * 生成统一的网页跳转 PendingIntent
     */
    public static PendingIntent createWebPendingIntent(Context context, int requestCode, String targetUrl) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
