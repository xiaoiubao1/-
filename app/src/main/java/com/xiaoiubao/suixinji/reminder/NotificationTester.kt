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
import com.xiaoiubao.suixinji.MainActivity

object NotificationTester {
    private const val CHANNEL_ID = "notification_test"
    private const val NOTIFICATION_ID = 90001

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)

    @android.annotation.SuppressLint("MissingPermission") // canNotify checks runtime permission; handle revocation too.
    fun send(context: Context) {
        if (!canNotify(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "通知测试", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "用于确认随心记通知权限和系统通知是否正常"
                }
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            data = android.net.Uri.parse("suixinji://notification/test")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("随心记通知测试成功")
            .setContentText("如果你看到这条通知，说明通知权限与通知渠道工作正常。")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "如果你看到这条通知，说明通知权限与通知渠道工作正常。准时提醒还需要在设置中检查闹钟权限。"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        catch (_: SecurityException) { /* Permission revoked between check and notify. */ }
    }
}
