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
 * 导航安全校验层：接收导航播报事件和摄像头帧，
 * 分析危险等级并发布到 [ServiceBus.hazardAlert]。
 *
 * ## 危险等级
 * - CRITICAL: 立即风险，需紧急干预
 * - WARNING:  潜在风险，需提示注意
 * - INFO:     信息性分析结果
 *
 * ## 当前状态
 * Phase 1 骨架实现，[analyzeHazard] 返回占位结果。
 * Phase 2 将集成 ML Kit / 自定义模型进行实际危险检测。
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
     * 监听导航播报事件流。
     */
    private fun observeNavigationEvents() {
        scope.launch {
            serviceBus.navigationEvent.collectLatest { navText ->
                val result = analyzeHazard(navText, null)
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
                    val result = analyzeHazard(navText = "", frame = frame)
                    if (result.level != HazardLevel.INFO) {
                        serviceBus.hazardAlert.tryEmit(result)
                    }
                } finally {
                    // NavSafetyEngine 是 cameraFrame 的消费者，负责关闭帧
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
     * @param navText 导航播报文本，空字符串表示仅帧分析
     * @param frame   Camera2 帧数据，null 表示仅文本分析
     * @return 危险分析结果
     */
    fun analyzeHazard(navText: String, frame: ImageProxy?): HazardResult {
        // Phase 2 实现：
        //   1. 从 navText 提取导航指令（转向、距离、车道）
        //   2. 从 frame 提取场景特征（物体检测、车道线、深度估计）
        //   3. 交叉验证：实际场景 vs 导航指令
        //   4. 输出危险等级 + 描述信息
        val message = when {
            navText.isNotEmpty() -> "导航播报: $navText"
            else -> "帧分析完成"
        }

        return HazardResult(
            level = HazardLevel.INFO,
            message = message,
            timestamp = System.currentTimeMillis()
        )
    }
}
