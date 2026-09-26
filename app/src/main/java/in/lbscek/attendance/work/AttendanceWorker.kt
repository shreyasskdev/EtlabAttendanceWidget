package `in`.lbscek.attendance.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import `in`.lbscek.attendance.data.AttendancePrefs
import `in`.lbscek.attendance.data.EtlabRepository
import `in`.lbscek.attendance.widget.AttendanceWidget
import java.util.concurrent.TimeUnit

class AttendanceWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = AttendancePrefs(applicationContext)
        val username = prefs.getUsername() ?: return Result.failure()
        val password = prefs.getPassword() ?: return Result.failure()

        return try {
            val repo = EtlabRepository()
            val fetchResult = repo.fetchAttendance(username, password)
            prefs.saveLastResult(fetchResult.attendance)
            AttendanceWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "attendance_periodic_refresh"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<AttendanceWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}