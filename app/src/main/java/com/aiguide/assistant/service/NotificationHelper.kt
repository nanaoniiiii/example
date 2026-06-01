package com.aiguide.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 前台服务通知辅助类。
 *
 * 负责创建通知渠道并构建持续通知，
 * 用于 AIGuideForegroundService 保持前台运行。
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "aiguide_foreground_service"
        const val CHANNEL_NAME = "AI 助盲"
        const val CHANNEL_DESCRIPTION = "AI 助盲辅助服务运行中"
        const val NOTIFICATION_ID = 1001
    }

    /**
     * 创建前台服务通知渠道。
     * 幂等操作，重复调用不会重复创建。
     */
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW  // LOW: 仅显示图标，不发出声音
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 构建前台服务持续通知。
     *
     * - 文案: "AI 助盲已就绪"
     * - 不可滑动删除（ongoing notification）
     * - 点击通知可回到应用
     */
    fun buildNotification(): Notification {
        // 打开 App 的 Intent（需要替换为实际 Launcher Activity）
        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = if (openAppIntent != null) {
            PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
        } else {
            null
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("AI 助盲已就绪")
            .setContentText("辅助服务正在运行")
            .setSmallIcon(android.R.drawable.ic_menu_compass)  // 需替换为实际 icon
            .setOngoing(true)            // 不可滑动删除
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
