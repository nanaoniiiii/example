package com.aiguide.assistant

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * 最小化测试 Activity - 不依赖任何自定义代码
 */
class TestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this)
        textView.text = "Hello from AIGuide Test!"
        textView.textSize = 24f
        
        setContentView(textView)
    }
}
