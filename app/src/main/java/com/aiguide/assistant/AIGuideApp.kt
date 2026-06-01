package com.aiguide.assistant

import android.app.Application
import com.aiguide.assistant.engine.FlashlightManager
import com.aiguide.assistant.engine.HazardDetector
import com.aiguide.assistant.engine.NavSafetyEngine
import com.aiguide.assistant.engine.NavigationListener
import com.aiguide.assistant.engine.VisionEngine
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AIGuideApp : Application() {

    @Inject
    lateinit var serviceBus: ServiceBus

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

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Hilt 在 super.onCreate() 后完成字段注入。
        // 所有 Engine 模块依赖 ServiceBus，由构造函数注入自动保证顺序。

        // Phase 2: 初始化 HazardDetector 和 FlashlightManager
        // HazardDetector 在 init 块中自动开始监听 cameraFrame 流
        // FlashlightManager 在 init 块中自动开始监听环境光和电源状态
    }

    companion object {
        lateinit var instance: AIGuideApp
            private set
    }
}
