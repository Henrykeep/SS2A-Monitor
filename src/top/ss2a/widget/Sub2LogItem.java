package top.ss2a.widget;

public class Sub2LogItem {
    public long id = 0;
    public String model = "未知模型";
    public String time = "--:--:--";
    public String cost = "$0.00";
    public String duration = "0s";
    public String tokensSummary = "0 Tokens";

    public Sub2LogItem() {}

    public Sub2LogItem(long id, String model, String time, String cost, String duration, String tokensSummary) {
        this.id = id;
        this.model = model;
        this.time = time;
        this.cost = cost;
        this.duration = duration;
        this.tokensSummary = tokensSummary;
    }
}