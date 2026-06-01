package com.aiguide.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * 核心无障碍服务：三击电源键 + 手势监听 + UI 操作引擎
 */
@AndroidEntryPoint
class AIGuideAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var serviceBus: ServiceBus

    // --- 三击电源键检测 ---
    private var powerKeyPressCount = 0
    private var lastPowerKeyTime = 0L
    private val powerKeyHandler = Handler(Looper.getMainLooper())
    private val tripleClickWindow = 1500L  // 1.5秒内完成三击
    private val resetRunnable = Runnable {
        powerKeyPressCount = 0
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 由各业务模块按需监听
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_POWER && event.action == KeyEvent.ACTION_DOWN) {
            return handlePowerKeyPress()
        }
        return super.onKeyEvent(event)
    }

    private fun handlePowerKeyPress(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastPowerKeyTime > tripleClickWindow) {
            powerKeyPressCount = 0
        }
        powerKeyPressCount++
        lastPowerKeyTime = now

        powerKeyHandler.removeCallbacks(resetRunnable)
        powerKeyHandler.postDelayed(resetRunnable, tripleClickWindow)

        if (powerKeyPressCount >= 3) {
            powerKeyPressCount = 0
            serviceBus.onTriplePowerClick()
            return true
        }
        return false
    }

    // --- 手势辅助 ---
    override fun onGesture(gestureId: Int): Boolean {
        serviceBus.onGestureDetected(gestureId)
        return super.onGesture(gestureId)
    }

    // --- UI 操作引擎 ---
    fun performClick(targetDescription: String) {
        val root = rootInActiveWindow ?: return
        val nodes = root.findAccessibilityNodeInfosByText(targetDescription)
        nodes.firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        root.recycle()
    }

    fun performScroll(yOffset: Float) {
        val path = Path().apply {
            moveTo(540f, 800f)
            lineTo(540f, 800f + yOffset)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
        powerKeyPressCount = 0
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceBus.onAccessibilityConnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        powerKeyHandler.removeCallbacks(resetRunnable)
    }
}
