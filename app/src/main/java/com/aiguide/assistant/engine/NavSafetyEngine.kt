package com.aiguide.assistant.engine

import androidx.camera.core.ImageProxy
import com.aiguide.assistant.service.HazardLevel
import com.aiguide.assistant.service.HazardResult
import com.aiguide.assistant.service.ServiceBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导航安全校验层：接收结构化导航指令 [NavInstruction] 和摄像头帧，
 * 分析危险等级并发布到 [ServiceBus.hazardAlert]。
 *
 * ## 危险等级
 * - CRITICAL: 立即风险，需紧急干预
 * - WARNING:  潜在风险，需提示注意
 * - INFO:     信息性分析结果
 *
 * ## 当前状态
 * Phase 1 骨架实现，[analyzeHazard] 返回占位结果。
 * Phase 2 集成 HazardDetector 进行实际危险检测，本层负责交叉验证。
 */
@Singleton
class NavSafetyEngine @Inject constructor(
    private val serviceBus: ServiceBus
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        observeNavigationEvents()
        observeCameraFrames()
    }

    /**
     * 监听结构化导航指令流。
     */
    private fun observeNavigationEvents() {
        scope.launch {
            serviceBus.navigationEvent.collectLatest { instruction ->
                val result = analyzeHazard(instruction = instruction, frame = null)
                if (result.level != HazardLevel.INFO) {
                    serviceBus.hazardAlert.tryEmit(result)
                }
            }
        }
    }

    /**
     * 监听摄像头帧流。
     * 消费后负责关闭 ImageProxy。
     */
    private fun observeCameraFrames() {
        scope.launch(Dispatchers.Default) {
            serviceBus.cameraFrame.collectLatest { frame ->
                try {
                    val result = analyzeHazard(instruction = null, frame = frame)
                    if (result.level != HazardLevel.INFO) {
                        serviceBus.hazardAlert.tryEmit(result)
                    }
                } finally {
                    frame.close()
                }
            }
        }
    }

    /**
     * 危险分析入口。
     *
     * Phase 1 骨架：返回 INFO 级别占位结果。
     * Phase 2 将实现：物体检测 / 场景理解 / 偏离判断 / 结合导航指令校验。
     *
     * @param instruction 导航指令，null 表示仅帧分析
     * @param frame       Camera2 帧数据，null 表示仅指令分析
     * @return 危险分析结果
     */
    fun analyzeHazard(instruction: NavInstruction?, frame: ImageProxy?): HazardResult {
        val message = when {
            instruction != null -> "导航指令: ${instruction.raw} (${instruction::class.simpleName}, ${instruction.distance}m)"
            frame != null       -> "帧分析完成"
            else                -> "空分析"
        }

        return HazardResult(
            level = HazardLevel.INFO,
            message = message,
            timestamp = System.currentTimeMillis()
        )
    }
}
