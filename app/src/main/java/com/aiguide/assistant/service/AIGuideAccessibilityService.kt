package com.aiguide.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * 核心无障碍服务：三击电源键 + 手势监听 + UI 操作引擎
 *
 * ## 新增 Phase 3 能力
 * - [performClick]：坐标点击（GestureDescription）
 * - [performSwipe]：坐标滑动
 * - [performTextInput]：向焦点输入框输入文字
 * - [performBack] / [performHome] / [performRecent] / [performNotifications]
 * - 通过 [instance] 静态引用暴露给 AutoEngine
 */
@AndroidEntryPoint
class AIGuideAccessibilityService : AccessibilityService() {

    companion object {
        /** 静态实例，供 AutoEngine 调用手势方法 */
        @Volatile
        var instance: AIGuideAccessibilityService? = null
            private set
    }

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

    /**
     * 通过文本描述查找并点击节点（保留 Phase 2 兼容）。
     */
    fun performClick(targetDescription: String) {
        val root = rootInActiveWindow ?: return
        val nodes = root.findAccessibilityNodeInfosByText(targetDescription)
        nodes.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        root.recycle()
    }

    /**
     * 坐标点击：在屏幕 (x, y) 位置生成一个瞬时点击手势。
     */
    fun performClick(x: Int, y: Int) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * 坐标滑动：从 (x1, y1) 到 (x2, y2)，持续 duration 毫秒。
     */
    fun performSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long = 300L) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * 向当前焦点输入框输入文字。
     */
    fun performTextInput(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focused.recycle()
        }
        root.recycle()
    }

    /** 系统返回键 */
    fun performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /** 回到桌面 */
    fun performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** 打开最近任务 */
    fun performRecent() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /** 下拉通知栏 */
    fun performNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
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
        instance = this
        serviceBus.onAccessibilityConnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        powerKeyHandler.removeCallbacks(resetRunnable)
    }
}
