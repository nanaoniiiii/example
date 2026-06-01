package com.aiguide.assistant.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.aiguide.assistant.service.ServiceBus
import com.aiguide.assistant.service.TtsPriority
import com.aiguide.assistant.service.TtsRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTS 播报管理器：基于 Android [TextToSpeech] 系统引擎，
 * 实现四级优先级队列播报。
 *
 * ## 优先级规则
 * | 优先级     | 行为                                            |
 * |-----------|-------------------------------------------------|
 * | CRITICAL  | 立即打断当前播报，清空队列，播报此条              |
 * | HIGH      | 等待当前句子结束后播报                            |
 * | NORMAL    | 追加到队尾，按序播报                              |
 * | LOW       | 追加到队尾，按序播报                              |
 *
 * ## 生命周期
 * - init 时初始化 TTS 引擎并注入 [UtteranceProgressListener]
 * - 监听 [ServiceBus.ttsRequest] 消费队列
 * - 支持 [stop] 中断当前+清空队列
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceBus: ServiceBus
) {

    companion object {
        private const val UTTERANCE_ID = "aiguide_tts_"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var utteranceCounter = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 播报队列（线程安全） */
    private val queue = ConcurrentLinkedQueue<TtsRequest>()

    /** 当前正在播报的请求 */
    private var currentRequest: TtsRequest? = null

    /** 当前句子是否正在播报 */
    private var isSpeaking = false

    init {
        initializeTts()
        startConsuming()
    }

    /**
     * 初始化 TTS 引擎。
     */
    private fun initializeTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        currentRequest = null
                        processNext()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        currentRequest = null
                        processNext()
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        isSpeaking = false
                        currentRequest = null
                        processNext()
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        isSpeaking = false
                        currentRequest = null
                        if (!interrupted) {
                            processNext()
                        }
                    }
                })

                // 尝试使用中文语音
                setChineseVoice()
            }
        }
    }

    /**
     * 优先选择中文语音引擎。
     */
    private fun setChineseVoice() {
        val tts = this.tts ?: return
        val voices: Set<Voice> = tts.voices
        // 优先匹配中文（zh-CN），其次 zh，再次任意包含"zh"的
        val chineseVoice = voices.firstOrNull { it.locale.language == "zh" && it.locale.country == "CN" }
            ?: voices.firstOrNull { it.locale.language == "zh" }
        if (chineseVoice != null) {
            tts.voice = chineseVoice
        } else {
            // 兜底：设置语言为中文
            tts.language = java.util.Locale.CHINESE
        }
    }

    /**
     * 开始监听 ServiceBus.ttsRequest，将请求入队。
     */
    private fun startConsuming() {
        scope.launch {
            serviceBus.ttsRequest.collectLatest { request ->
                enqueue(request)
            }
        }
    }

    /**
     * 将 TTS 请求入队，并按优先级处理。
     */
    private fun enqueue(request: TtsRequest) {
        when (request.priority) {
            TtsPriority.CRITICAL -> {
                // CRITICAL: 立即打断当前播报，清空队列
                stopImmediately()
                queue.clear()
                queue.add(request)
                processNext()
            }
            TtsPriority.HIGH -> {
                // HIGH: 等待当前句子结束后插队到队首
                val head = queue.peek()
                if (head?.priority == TtsPriority.CRITICAL) {
                    // CRITICAL 正在/即将播，插入到 CRITICAL 之后
                    queue.clear()
                    queue.add(head)
                    queue.add(request)
                } else {
                    // 放入队首
                    val tempQueue = ConcurrentLinkedQueue<TtsRequest>()
                    tempQueue.add(request)
                    tempQueue.addAll(queue)
                    queue.clear()
                    queue.addAll(tempQueue)
                }
                if (!isSpeaking) {
                    processNext()
                }
            }
            TtsPriority.NORMAL, TtsPriority.LOW -> {
                // NORMAL / LOW: 追加到队尾
                queue.add(request)
                if (!isSpeaking) {
                    processNext()
                }
            }
        }
    }

    /**
     * 处理队列中的下一个请求。
     */
    private fun processNext() {
        val request = queue.poll() ?: return
        currentRequest = request
        speak(request)
    }

    /**
     * 执行 TTS 播报。
     */
    private fun speak(request: TtsRequest) {
        val tts = this.tts
        if (!isInitialized || tts == null) {
            // TTS 未就绪，丢弃此请求
            processNext()
            return
        }

        val utteranceId = UTTERANCE_ID + (utteranceCounter++)
        val params = Bundle()

        val result = tts.speak(request.text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            // 播报失败，跳过
            currentRequest = null
            processNext()
        }
    }

    /**
     * 立即停止当前播报（不触发 onDone）。
     */
    private fun stopImmediately() {
        tts?.stop()
        isSpeaking = false
        currentRequest = null
    }

    /**
     * 停止当前播报并清空队列。
     */
    fun stop() {
        stopImmediately()
        queue.clear()
    }

    /**
     * 检查 TTS 是否已初始化。
     */
    fun isReady(): Boolean = isInitialized

    /**
     * 释放 TTS 资源。
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
