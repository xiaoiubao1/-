package com.xiaoiubao.suixinji.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.xiaoiubao.suixinji.MainActivity
import com.xiaoiubao.suixinji.data.EventDatabase

class CourseReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val courseId = inputData.getLong(KEY_COURSE_ID, 0L)
        val name = inputData.getString(KEY_NAME).orEmpty().ifBlank { "课程提醒" }
        val location = inputData.getString(KEY_LOCATION).orEmpty()
        val startMinute = inputData.getInt(KEY_START_MINUTE, 8 * 60)
        val minutesBefore = inputData.getInt(KEY_MINUTES_BEFORE, 10)

        createChannel()

        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_COURSE_ID, courseId)
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                (100000 + courseId).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val timeText = "%02d:%02d".format(startMinute / 60, startMinute % 60)
            val text = buildString {
                append(if (minutesBefore == 0) "$timeText 开始" else "$minutesBefore 分钟后开始 · $timeText")
                if (location.isNotBlank()) append(" · $location")
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(name)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(applicationContext)
                .notify((100000 + courseId).toInt(), notification)
        }

        EventDatabase(applicationContext).use { db ->
            db.getCourse(courseId)?.let { current ->
                if (current.reminderEnabled) CourseReminderScheduler.schedule(applicationContext, current)
            }
        }
        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "课程提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "课程开始前的提醒通知"
                }
            )
        }
    }

    companion object {
        const val KEY_COURSE_ID = "course_id"
        const val KEY_NAME = "name"
        const val KEY_LOCATION = "location"
        const val KEY_START_MINUTE = "start_minute"
        const val KEY_MINUTES_BEFORE = "minutes_before"
        private const val CHANNEL_ID = "course_reminders"
    }
}
