package top.ss2a.widget.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import top.ss2a.widget.data.Sub2ApiClient
import top.ss2a.widget.data.WidgetDataStore
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val store = WidgetDataStore(applicationContext)
        val client = Sub2ApiClient(store)

        return try {
            val stats = client.fetchDashboardStats()
            Sub2WidgetProvider.updateAllWidgets(applicationContext, stats)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "ss2a_widget_periodic_sync"

        fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val actualInterval = intervalMinutes.coerceAtLeast(15) // Android WorkManager 周期任务最低 15 分钟

            val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                actualInterval,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}