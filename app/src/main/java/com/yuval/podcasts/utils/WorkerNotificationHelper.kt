package com.yuval.podcasts.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo

object WorkerNotificationHelper {
    fun createForegroundInfo(
        context: Context,
        notificationId: Int,
        channelId: String,
        channelName: String,
        title: String,
        contentText: String,
        progress: Int,
        maxProgress: Int,
        isIndeterminate: Boolean = maxProgress == 0
    ): ForegroundInfo {
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(maxProgress, progress, isIndeterminate)
            .setOngoing(true)
            .build()

        return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
