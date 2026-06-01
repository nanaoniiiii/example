package com.aiguide.assistant

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 确保 ServiceBus 最先初始化（显式触发注入）
        // Hilt 在 super.onCreate() 后完成字段注入
        // Engine 模块依赖 ServiceBus，由构造函数注入自动保证顺序
    }

    companion object {
        lateinit var instance: AIGuideApp
            private set
    }
}
