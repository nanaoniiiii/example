package com.aiguide.assistant.service

import com.aiguide.assistant.engine.NavInstruction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.camera.core.ImageProxy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service 总线：基于 SharedFlow + StateFlow 的模块间通信中枢
 *
 * 所有模块通过总线发布/订阅事件，解耦模块依赖。
 */
@Singleton
class ServiceBus @Inject constructor() {

    // ========================
    // 事件通道 (SharedFlow)
    // ========================

    /** 三击电源键事件 */
    private val _triplePowerClick = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val triplePowerClick = _triplePowerClick.asSharedFlow()

    /** 手势事件 (gestureId) */
    private val _gesture = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val gesture = _gesture.asSharedFlow()

    /** 语音指令 (识别文本) */
    private val _voiceCommand = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val voiceCommand = _voiceCommand.asSharedFlow()

    /** TTS 播报请求 (文本 + 优先级) */
    private val _ttsRequest = MutableSharedFlow<TtsRequest>(extraBufferCapacity = 16)
    val ttsRequest = _ttsRequest.asSharedFlow()

    /** 视觉分析结果 */
    private val _visionResult = MutableSharedFlow<VisionResult>(extraBufferCapacity = 8)
    val visionResult = _visionResult.asSharedFlow()

    /** 导航偏离预警 */
    private val _navDeviationWarning = MutableSharedFlow<NavWarning>(extraBufferCapacity = 4)
    val navDeviationWarning = _navDeviationWarning.asSharedFlow()

    /** Camera2 帧流 (YUV ImageProxy) */
    val cameraFrame = MutableSharedFlow<ImageProxy>(extraBufferCapacity = 16)

    /** 导航播报事件 — Phase 2 升级为结构化 NavInstruction */
    val navigationEvent = MutableSharedFlow<NavInstruction>(extraBufferCapacity = 8)

    /** 危险分析结果 */
    val hazardAlert = MutableSharedFlow<HazardResult>(extraBufferCapacity = 8)

    // ========================
    // 状态通道 (StateFlow)
    // ========================

    /** 协助模式状态 */
    private val _assistMode = MutableStateFlow(AssistMode.IDLE)
    val assistMode: StateFlow<AssistMode> = _assistMode

    /** 隐私蒙版透明度 0-100 */
    private val _privacyOverlayAlpha = MutableStateFlow(0)
    val privacyOverlayAlpha: StateFlow<Int> = _privacyOverlayAlpha

    /** 无障碍服务是否已连接 */
    private val _accessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected: StateFlow<Boolean> = _accessibilityConnected

    /** 电池优化豁免状态 */
    private val _batteryOptimizationExempt = MutableStateFlow(false)
    val batteryOptimizationExempt: StateFlow<Boolean> = _batteryOptimizationExempt

    /** 摄像头开关状态 */
    var cameraEnabled = MutableStateFlow(false)

    /** 环境光 lux（实时读取） */
    var environmentLux = MutableStateFlow(0f)

    /** 手电筒闪光灯是否正在工作 */
    var flashLightEnabled = MutableStateFlow(false)

    /** 当前环境是否判定为天黑（lux < 10） */
    var isDarkEnvironment = MutableStateFlow(false)

    // ========================
    // 事件发射方法
    // ========================

    fun onTriplePowerClick() {
        _triplePowerClick.tryEmit(Unit)
    }

    fun onGestureDetected(gestureId: Int) {
        _gesture.tryEmit(gestureId)
    }

    fun onVoiceCommand(text: String) {
        _voiceCommand.tryEmit(text)
    }

    fun requestTts(text: String, priority: TtsPriority) {
        _ttsRequest.tryEmit(TtsRequest(text, priority))
    }

    fun onVisionResult(result: VisionResult) {
        _visionResult.tryEmit(result)
    }

    fun onNavWarning(warning: NavWarning) {
        _navDeviationWarning.tryEmit(warning)
    }

    fun setAssistMode(mode: AssistMode) {
        _assistMode.value = mode
    }

    fun setPrivacyOverlayAlpha(alpha: Int) {
        _privacyOverlayAlpha.value = alpha.coerceIn(0, 100)
    }

    fun onAccessibilityConnected() {
        _accessibilityConnected.value = true
    }

    fun setBatteryOptimizationExempt(exempt: Boolean) {
        _batteryOptimizationExempt.value = exempt
    }
}

// ========================
// 数据类
// ========================

enum class AssistMode { IDLE, PASSIVE, ACTIVE }

enum class TtsPriority { CRITICAL, HIGH, NORMAL, LOW }

data class TtsRequest(val text: String, val priority: TtsPriority)

data class VisionResult(
    val type: VisionType,
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBox? = null,
    val description: String = ""
)

enum class VisionType { OBJECT, TEXT, SCENE, DEPTH }

data class BoundingBox(val x: Float, val y: Float, val width: Float, val height: Float)

data class NavWarning(
    val type: NavWarningType,
    val message: String,
    val severity: Int  // 1-3，越高越危险
)

enum class NavWarningType { OFF_ROUTE, OBSTACLE, WRONG_DIRECTION }

/** 三级危险等级 */
enum class HazardLevel { CRITICAL, WARNING, INFO }

/** 危险分析结果（Phase 2 增强：支持检测类型 + 边界框 + 距离估算） */
data class HazardResult(
    val level: HazardLevel,
    val message: String,
    /** 检测目标类型：人/车/自行车/障碍物/马路边缘/急坡 */
    val type: String = "",
    /** 目标边界框（图像坐标，归一化或像素坐标） */
    val bbox: BoundingBox? = null,
    /** 粗略距离估算：近/中/远 */
    val distance: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
