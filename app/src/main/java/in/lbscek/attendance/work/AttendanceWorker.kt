package `in`.lbscek.attendance.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import `in`.lbscek.attendance.data.AttendancePrefs
import `in`.lbscek.attendance.data.EtlabRepository
import `in`.lbscek.attendance.widget.AttendanceWidget
import java.util.concurrent.TimeUnit
import androidx.glance.appwidget.updateAll

class AttendanceWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = AttendancePrefs(applicationContext)
        val username = prefs.getUsername() ?: return Result.failure()
        val password = prefs.getPassword() ?: return Result.failure()
        val semester = prefs.getSemester()

        return try {
            val repo = EtlabRepository()
            val result = repo.fetchAttendance(username, password, semester)
            prefs.saveLastResult(result)
            AttendanceWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "attendance_periodic_refresh"

        /** Refresh a few times a day; Etlab attendance doesn't change more often than that. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<AttendanceWorker>(3, TimeUnit.HOURS)
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

        /** Used by the widget's "Refresh now" tap target. */
        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<AttendanceWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
