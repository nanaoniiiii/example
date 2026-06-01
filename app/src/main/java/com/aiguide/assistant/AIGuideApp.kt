package com.aiguide.assistant

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AIGuideApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("AIGuideApp", "onCreate: AIGuideApp initialized")
    }

    companion object {
        lateinit var instance: AIGuideApp
            private set
    }
}
