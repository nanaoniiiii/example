package com.aiguide.assistant.engine

import com.aiguide.assistant.service.ServiceBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音引擎：集成 Vosk Android SDK 离线唤醒词 + 流式 ASR。
 *
 * ## 工作流程
 * 1. 启动后持续监听唤醒词"小助"
 * 2. 唤醒后进入流式 ASR 收音阶段
 * 3. 2 秒静默自动结束本轮收音
 * 4. 识别结果通过 [ServiceBus.voiceCommand] 发出
 * 5. 单次对话内保持上下文（currentSession），跨对话遗忘
 *
 * ## 语音打断
 * 识别到新语音时，通过 [resultCallback] 通知外部取消当前 TTS 播报。
 */
@Singleton
class VoiceEngine @Inject constructor(
    private val serviceBus: ServiceBus
) {

    companion object {
        /** 唤醒词 */
        const val WAKE_WORD = "小助"

        /** 静默超时（毫秒），2 秒无声自动结束收音 */
        private const val SILENCE_TIMEOUT_MS = 2000L
    }

    /**
     * 对话会话上下文：单次对话期间保持，跨对话清空。
     */
    data class SessionContext(
        val sessionId: Long,
        val transcriptions: MutableList<String> = mutableListOf(),
        var lastActivityTime: Long = System.currentTimeMillis()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isAwake = MutableStateFlow(false)
    val isAwake: StateFlow<Boolean> = _isAwake

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private var currentSession: SessionContext? = null
    private var sessionCounter: Long = 0L

    private var silenceTimerJob: Job? = null

    /**
     * 语音识别结果回调：返回 true 表示检测到新语音且需要打断当前 TTS。
     * 外部（如 TtsManager）可注入此回调实现语音打断。
     */
    var resultCallback: ((text: String) -> Boolean)? = null

    /**
     * 初始化 Vosk 唤醒词模型。
     * 实际实现需加载 Vosk 离线模型文件（通常放在 assets/ 或外部存储）。
     *
     * @param modelPath Vosk 模型路径，为空则使用默认路径
     */
    fun initialize(modelPath: String? = null) {
        // Vosk Android SDK 初始化:
        //   SpeechService speechService = new SpeechService(...);
        //   配置唤醒词："小助"
        //   配置静默超时: SILENCE_TIMEOUT_MS
        // 此处为框架代码，实际集成：
        // - Vosk recognizer 需在 assets/models/ 下放置模型
        // - 通过 Vosk recognizer.startListening() / stop() 控制
        // - 结果回调中调用 onPartialResult / onResult
    }

    /**
     * 启动唤醒词监听。
     */
    fun startWakeWordDetection() {
        // Vosk SDK:
        //   recognizer.addListener(wakeWordListener)
        //   recognizer.startListening(keywords = listOf(WAKE_WORD))
    }

    /**
     * 当唤醒词被检测到时调用。
     */
    fun onWakeWordDetected() {
        _isAwake.value = true
        startSession()
        // 可选：播放唤醒提示音
        serviceBus.requestTts("我在", com.aiguide.assistant.service.TtsPriority.HIGH)
    }

    /**
     * 开始新的对话会话。
     */
    private fun startSession() {
        sessionCounter++
        currentSession = SessionContext(sessionId = sessionCounter)
        _isListening.value = true
        resetSilenceTimer()
    }

    /**
     * 结束当前对话会话，清空上下文。
     */
    fun endSession() {
        _isAwake.value = false
        _isListening.value = false
        silenceTimerJob?.cancel()
        silenceTimerJob = null
        currentSession = null
        // 回到唤醒词监听状态
        startWakeWordDetection()
    }

    /**
     * 处理流式 ASR 的中间结果。
     */
    fun onPartialResult(text: String) {
        val session = currentSession ?: return
        session.lastActivityTime = System.currentTimeMillis()

        // 语音打断：回调通知外部
        if (resultCallback?.invoke(text) == true) {
            // 外部确认需要打断 TTS
        }

        resetSilenceTimer()
    }

    /**
     * 处理流式 ASR 的最终识别结果。
     */
    fun onFinalResult(text: String) {
        val session = currentSession ?: return
        session.transcriptions.add(text)
        session.lastActivityTime = System.currentTimeMillis()

        // 发布语音指令
        serviceBus.onVoiceCommand(text)

        // 检测是否是退出指令
        if (text.contains("关闭协助") || text.contains("退出协助") ||
            text.contains("退出") && session.transcriptions.size == 1) {
            endSession()
            return
        }

        // 如果有后续语音，继续收音
        resetSilenceTimer()
    }

    /**
     * 重置静默计时器：2 秒无声音自动结束。
     */
    private fun resetSilenceTimer() {
        silenceTimerJob?.cancel()
        silenceTimerJob = scope.launch {
            delay(SILENCE_TIMEOUT_MS)
            // 超时：结束当前收音，但保持唤醒状态
            onSilenceDetected()
        }
    }

    /**
     * 静默超时处理：结束本轮收音，回到唤醒词监听。
     */
    private fun onSilenceDetected() {
        _isListening.value = false
        // 如果当前在对话中且没有收到任何结果，直接结束会话
        val session = currentSession
        if (session != null && session.transcriptions.isEmpty()) {
            endSession()
        } else if (session != null) {
            // 有内容的对话：等待后续处理或新唤醒
            endSession()
        }
    }

    /**
     * 检查当前是否有活跃的对话会话。
     */
    fun hasActiveSession(): Boolean = currentSession != null

    /**
     * 获取当前会话上下文（如有）。
     */
    fun getCurrentSession(): SessionContext? = currentSession
}
