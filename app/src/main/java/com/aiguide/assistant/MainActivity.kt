package com.aiguide.assistant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * AIGuide 主 Activity
 *
 * 应用入口，负责 UI 展示与用户交互。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewBinding will be used here when layout is available
        // setContentView(binding.root)
    }
}
