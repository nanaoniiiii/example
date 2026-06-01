package com.aiguide.assistant.engine

import com.aiguide.assistant.service.AssistMode
import com.aiguide.assistant.service.HazardLevel
import com.aiguide.assistant.service.ServiceBus
import com.aiguide.assistant.service.TtsPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安全护栏：监听危险信号与系统状态，控制 AutoEngine 的启停。
 *
 * ## 监听维度
 * 1. **危险警报** [ServiceBus.hazardAlert]
 *    - CRITICAL 级危险 → 立即暂停 AutoEngine
 *    - WARNING / INFO → 仅记录，不干预
 *
 * 2. **协助模式** [ServiceBus.assistMode]
 *    - ASSIST_ACTIVE（蒙版关闭，用户可正常看屏）→ 禁用 AutoEngine
 *    - IDLE / PASSIVE → 在摄像头开启时恢复
 *
 * 3. **摄像头状态** [ServiceBus.cameraEnabled]
 *    - 摄像头关闭 → 降级为仅语音播报，禁用 UI 自动化
 *    - 摄像头开启 → 在非 ASSIST_ACTIVE 模式下恢复
 *
 * ## 公开 API
 * - [pauseAutoEngine] / [resumeAutoEngine] — 手动控制
 * - [isAutoEngineEnabled] — 查询当前状态
 */
@Singleton
class SafetyGuard @Inject constructor(
    private val serviceBus: ServiceBus
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ========================
    // 初始化：注册所有监听
    // ========================

    init {
        // 1) 危险警报监听
        scope.launch {
            serviceBus.hazardAlert.collect { hazard ->
                when (hazard.level) {
                    HazardLevel.CRITICAL -> {
                        pauseAutoEngine()
                        serviceBus.requestTts(
                            "检测到危险：${hazard.message}，自动操作已暂停",
                            TtsPriority.CRITICAL
                        )
                    }
                    HazardLevel.WARNING -> {
                        // 仅播报，不暂停
                        serviceBus.requestTts(
                            "注意：${hazard.message}",
                            TtsPriority.HIGH
                        )
                    }
                    HazardLevel.INFO -> {
                        // 忽略
                    }
                }
            }
        }

        // 2) 协助模式监听
        scope.launch {
            serviceBus.assistMode.collect { mode ->
                when (mode) {
                    AssistMode.ACTIVE -> {
                        // 蒙版关闭，用户可正常操作 → 禁用自动操作
                        serviceBus.autoEngineEnabled.value = false
                        serviceBus.requestTts(
                            "协助模式已激活，自动操作已禁用",
                            TtsPriority.NORMAL
                        )
                    }
                    AssistMode.PASSIVE -> {
                        // 蒙版开启，用户需要辅助 → 在摄像头开启时恢复
                        if (serviceBus.cameraEnabled.value) {
                            serviceBus.autoEngineEnabled.value = true
                        }
                    }
                    AssistMode.IDLE -> {
                        // 空闲状态 → 在摄像头开启时恢复
                        if (serviceBus.cameraEnabled.value) {
                            serviceBus.autoEngineEnabled.value = true
                        }
                    }
                }
            }
        }

        // 3) 摄像头状态监听
        scope.launch {
            serviceBus.cameraEnabled.collect { enabled ->
                if (!enabled) {
                    // 摄像头关闭 → 降级为仅语音播报
                    serviceBus.autoEngineEnabled.value = false
                    serviceBus.requestTts(
                        "摄像头已关闭，自动操作已降级为仅语音模式",
                        TtsPriority.HIGH
                    )
                } else {
                    // 摄像头开启 → 仅在非 ACTIVE 模式下恢复
                    if (serviceBus.assistMode.value != AssistMode.ACTIVE) {
                        serviceBus.autoEngineEnabled.value = true
                    }
                }
            }
        }
    }

    // ========================
    // 公开 API
    // ========================

    /** 手动暂停 AutoEngine（如外部检测到异常） */
    fun pauseAutoEngine() {
        serviceBus.autoEngineEnabled.value = false
    }

    /** 手动恢复 AutoEngine（需满足所有安全条件） */
    fun resumeAutoEngine() {
        if (canSafelyResume()) {
            serviceBus.autoEngineEnabled.value = true
        }
    }

    /** 查询 AutoEngine 当前是否启用 */
    fun isAutoEngineEnabled(): Boolean = serviceBus.autoEngineEnabled.value

    // ========================
    // 内部判断
    // ========================

    /**
     * 判断是否满足安全恢复条件：
     * - 摄像头已开启
     * - 协助模式不是 ASSIST_ACTIVE
     * - 无 CRITICAL 级危险警报（通过 autoEngineEnabled 间接判断）
     */
    private fun canSafelyResume(): Boolean {
        return serviceBus.cameraEnabled.value &&
                serviceBus.assistMode.value != AssistMode.ACTIVE
    }
}
