package com.aiguide.assistant

import android.app.Application
import com.aiguide.assistant.engine.AutoEngine
import com.aiguide.assistant.engine.DeviceProfile
import com.aiguide.assistant.engine.FlashlightManager
import com.aiguide.assistant.engine.HazardDetector
import com.aiguide.assistant.engine.NavSafetyEngine
import com.aiguide.assistant.engine.NavigationListener
import com.aiguide.assistant.engine.PerformanceOptimizer
import com.aiguide.assistant.engine.SafetyGuard
import com.aiguide.assistant.engine.VisionEngine
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AIGuideApp : Application() {

    @Inject
    lateinit var serviceBus: ServiceBus

    @Inject
    lateinit var deviceProfile: DeviceProfile

    @Inject
    lateinit var performanceOptimizer: PerformanceOptimizer

    @Inject
    lateinit var visionEngine: VisionEngine

    @Inject
    lateinit var navigationListener: NavigationListener

    @Inject
    lateinit var navSafetyEngine: NavSafetyEngine

    @Inject
    lateinit var hazardDetector: HazardDetector

    @Inject
    lateinit var flashlightManager: FlashlightManager

    @Inject
    lateinit var autoEngine: AutoEngine

    @Inject
    lateinit var safetyGuard: SafetyGuard

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Hilt 在 super.onCreate() 后完成字段注入。
        // Phase 4: DeviceProfile 和 PerformanceOptimizer 在 init 块中自动完成初始化，
        // 必须在 VisionEngine / HazardDetector / FlashlightManager 之前完成，
        // 以保证 performanceParams / deviceProfile 已发布到 ServiceBus。
        // Hilt 注入顺序已保证：deviceProfile → performanceOptimizer → 其他 Engine。

        // Phase 2: 初始化 HazardDetector 和 FlashlightManager
        // HazardDetector 在 init 块中自动开始监听 cameraFrame 流
        // FlashlightManager 在 init 块中自动开始监听环境光和电源状态

        // Phase 3: AutoEngine 在 init 块中自动监听 voiceCommand 流；
        // SafetyGuard 在 init 块中自动监听 hazardAlert / assistMode / cameraEnabled
    }

    companion object {
        lateinit var instance: AIGuideApp
            private set
    }
}
