package com.aiguide.assistant.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.aiguide.assistant.MainActivity

/**
 * 智能待机 Foreground Service：保活 + 三档状态管理
 *
 * - IDLE:  仅监听三击唤醒，最低功耗
 * - PASSIVE: 三击已激活，等待语音，中等功耗
 * - ACTIVE:  正在分析/播报/引导，全功耗
 */
@AndroidEntryPoint
class AIGuideForegroundService : Service() {

    @Inject
    lateinit var serviceBus: ServiceBus

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "aiguide_foreground"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, NotificationHelper.buildNotification(this))
        observeBus()
    }

    private fun observeBus() {
        // 三击电源键 → 切换到 PASSIVE 模式
        serviceBus.triplePowerClick.collectLatest {
            serviceBus.setAssistMode(AssistMode.PASSIVE)
            serviceBus.requestTts("我在听", TtsPriority.HIGH)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
