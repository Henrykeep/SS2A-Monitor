package top.ss2a.widget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public class Sub2JobService extends JobService {
    public static final int JOB_ID = 20261005;

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WidgetDataStore store = new WidgetDataStore(getApplicationContext());
                    Sub2ApiClient client = new Sub2ApiClient(store);
                    Sub2DashboardData fresh = client.fetchDashboardStats(true);
                    java.util.List<Sub2LogItem> logs = client.fetchRecentLogs(4);
                    if (logs != null && !logs.isEmpty()) {
                        store.saveRecentLogs(logs);
                    } else {
                        logs = store.getCachedRecentLogs();
                    }
                    Sub2WidgetProvider.updateAllWidgets(getApplicationContext(), fresh);
                    Sub2FullStatsWidgetProvider.updateAllWidgets(getApplicationContext(), fresh);
                    Sub2LogsWidgetProvider.updateAllWidgets(getApplicationContext(), fresh, logs);
                    Sub2LatencyWidgetProvider.updateAllWidgets(getApplicationContext(), fresh, logs);
                } catch (Exception ignored) {
                } finally {
                    jobFinished(params, false);
                }
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    public static void schedulePeriodicJob(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                ComponentName serviceComponent = new ComponentName(context, Sub2JobService.class);
                JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceComponent);
                // 周期设置为15分钟（Android JobScheduler 允许的最低周期）
                builder.setPeriodic(15 * 60 * 1000L);
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
                builder.setPersisted(true); // 设备重启后保留
                scheduler.schedule(builder.build());
            }
        } catch (Exception ignored) {}
    }

    public static void cancelJob(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                scheduler.cancel(JOB_ID);
            }
        } catch (Exception ignored) {}
    }
}