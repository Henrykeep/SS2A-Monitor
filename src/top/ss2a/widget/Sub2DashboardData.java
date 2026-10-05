package top.ss2a.widget;

public class Sub2DashboardData {
    public String modeTitle = "全站监控"; // "全站监控" 或 "个人用量"
    public String lastUpdateTime = "--:--";
    public boolean isSuccess = true;
    public String errorMessage = "";

    // === 今日用量数据 ===
    public String todayCost = "$0.00";
    public long todayRequests = 0;
    public String todayTokens = "0";
    public String todayInputTokens = "0";
    public String todayOutputTokens = "0";
    public String todayCacheReadTokens = "0";

    // === 累计/全部用量数据 ===
    public String totalCost = "$0.00";
    public long totalRequests = 0;
    public String totalTokens = "0";
    public String totalInputTokens = "0";
    public String totalOutputTokens = "0";
    public String totalCacheReadTokens = "0";

    // === 实时负载与密钥指标 ===
    public int rpm = 0;
    public String tpm = "0";
    public String avgDuration = "0s";
    public int totalKeys = 0;
    public int activeKeys = 0;
    public int activeUsers = 0;
}
