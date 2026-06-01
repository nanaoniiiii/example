package com.aiguide.assistant.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 智能待机 Foreground Service：保活 + 三档状态管理
 *
 * - IDLE:  仅监听三击唤醒，最低功耗
 * - PASSIVE: 三击已激活，等待语音，中等功耗
 * - ACTIVE:  正在分析/播报/引导，全功耗
 */
@AndroidEntryPoint
class AIGuideForegroundService : LifecycleService() {

    @Inject
    lateinit var serviceBus: ServiceBus

    @Inject
    lateinit var notificationHelper: NotificationHelper

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "aiguide_foreground"
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        startForeground(NOTIFICATION_ID, notificationHelper.buildNotification())
        observeBus()
    }

    private fun observeBus() {
        lifecycleScope.launch {
            // 三击电源键 → 切换到 PASSIVE 模式
            serviceBus.triplePowerClick.collectLatest {
                serviceBus.setAssistMode(AssistMode.PASSIVE)
                serviceBus.requestTts("我在听", TtsPriority.HIGH)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
