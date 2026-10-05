package top.ss2a.widget;

public class Sub2LogItem {
    public long id = 0;
    public String model = "未知模型";
    public String displayModel = "未知模型";
    public String time = "--:--:--";
    public String cost = "$0.00";
    public double rawCost = 0.0;
    public long durationMs = 0;
    public String duration = "0s";
    public String tokensSummary = "0 Tokens";
    public boolean hasCacheHit = false;
    public String account = "";

    public Sub2LogItem() {}

    public Sub2LogItem(long id, String model, String time, String cost, String duration, String tokensSummary) {
        this.id = id;
        this.model = model;
        this.displayModel = cleanModelName(model);
        this.time = time;
        this.cost = cost;
        this.duration = duration;
        this.tokensSummary = tokensSummary;
        this.account = "";
    }

    public static String cleanModelName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "未知模型";
        String s = raw.trim();
        int slashIdx = s.indexOf("/");
        if (slashIdx >= 0 && slashIdx < s.length() - 1) {
            s = s.substring(slashIdx + 1);
        }
        if (s.matches(".*-[0-9]{8}$")) {
            s = s.substring(0, s.length() - 9);
        }
        return s;
    }
}
