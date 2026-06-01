package com.aiguide.assistant

import android.app.Application
import android.util.Log

// 暂时移除 @HiltAndroidApp，测试纯 Android Application 是否能启动
class AIGuideApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("AIGuideApp", "onCreate: success - pure Android version")
    }

    companion object {
        lateinit var instance: AIGuideApp
            private set
    }
}
