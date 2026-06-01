package com.aiguide.assistant.engine

import com.aiguide.assistant.service.AssistMode
import com.aiguide.assistant.service.ServiceBus
import com.aiguide.assistant.service.TtsPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 协助模式状态机。
 *
 * 状态流转：
 *   IDLE           → 初始状态
 *   PRIVACY_ACTIVE  = PASSIVE（隐私蒙版开启）
 *   ASSIST_ACTIVE   = ACTIVE（蒙版关闭，他人协助中）
 *
 * ## ASSIST_ACTIVE 超时机制
 * - 3 分钟无操作自动恢复 PRIVACY_ACTIVE
 * - 每 60 秒 TTS 播报剩余时间
 *
 * ## 退出 ASSIST_ACTIVE 的方式
 * 1. 三击电源键
 * 2. 语音指令"关闭协助"
 * 3. 加速度计剧烈晃动
 * 4. 翻转朝下（屏幕朝下）
 */
@Singleton
class AssistModeManager @Inject constructor(
    private val serviceBus: ServiceBus
) {

    companion object {
        /** ASSIST_ACTIVE 模式超时时间（毫秒） */
        private const val ASSIST_TIMEOUT_MS = 3 * 60 * 1000L  // 3 分钟
        /** TTS 提示间隔（毫秒） */
        private const val TTS_REMINDER_INTERVAL_MS = 60 * 1000L  // 60 秒
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var timeoutJob: Job? = null
    private var reminderJob: Job? = null

    init {
        startListening()
    }

    /**
     * 当前所处的协助模式。
     */
    val currentMode: AssistMode
        get() = serviceBus.assistMode.value

    /**
     * 开始监听三击事件和语音指令。
     */
    private fun startListening() {
        // 监听三击电源键：切换 ASSIST_ACTIVE / PRIVACY_ACTIVE
        scope.launch {
            serviceBus.triplePowerClick.collectLatest {
                onTripleClick()
            }
        }

        // 监听语音指令：识别到"关闭协助"则退出 ASSIST_ACTIVE
        scope.launch {
            serviceBus.voiceCommand.collectLatest { text ->
                if (text.contains("关闭协助") || text.contains("退出协助")) {
                    exitAssistActive()
                }
            }
        }
    }

    /**
     * 处理三击事件：IDLE/PRIVACY_ACTIVE → ASSIST_ACTIVE，ASSIST_ACTIVE → PRIVACY_ACTIVE
     */
    private fun onTripleClick() {
        when (serviceBus.assistMode.value) {
            AssistMode.IDLE, AssistMode.PASSIVE -> {
                enterAssistActive()
            }
            AssistMode.ACTIVE -> {
                exitAssistActive()
            }
        }
    }

    /**
     * 进入 ASSIST_ACTIVE 模式（蒙版关闭，他人协助）。
     */
    private fun enterAssistActive() {
        serviceBus.setAssistMode(AssistMode.ACTIVE)
        serviceBus.setPrivacyOverlayAlpha(0)  // 关闭蒙版

        // 启动超时定时器
        timeoutJob = scope.launch {
            delay(ASSIST_TIMEOUT_MS)
            exitAssistActive()
        }

        // 启动 TTS 提醒定时器
        reminderJob = scope.launch {
            delay(TTS_REMINDER_INTERVAL_MS)
            while (true) {
                val remainingMinutes = 2 // 已过 1 分钟，剩余 2 分钟
                serviceBus.requestTts("协助模式剩余 ${remainingMinutes} 分钟", TtsPriority.HIGH)
                delay(TTS_REMINDER_INTERVAL_MS)
            }
        }
    }

    /**
     * 退出 ASSIST_ACTIVE，恢复到 PRIVACY_ACTIVE（默认隐私状态）。
     */
    private fun exitAssistActive() {
        timeoutJob?.cancel()
        timeoutJob = null
        reminderJob?.cancel()
        reminderJob = null

        serviceBus.setAssistMode(AssistMode.PASSIVE)
        serviceBus.setPrivacyOverlayAlpha(100)  // 恢复蒙版
    }

    /**
     * 加速度计检测到剧烈晃动时调用。
     * 如果当前在 ASSIST_ACTIVE，则退出。
     */
    fun onShakeDetected() {
        if (serviceBus.assistMode.value == AssistMode.ACTIVE) {
            exitAssistActive()
        }
    }

    /**
     * 检测到翻转朝下时调用。
     * 如果当前在 ASSIST_ACTIVE，则退出。
     */
    fun onFaceDownDetected() {
        if (serviceBus.assistMode.value == AssistMode.ACTIVE) {
            exitAssistActive()
        }
    }
}
