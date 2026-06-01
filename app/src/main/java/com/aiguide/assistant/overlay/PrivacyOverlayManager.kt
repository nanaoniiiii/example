package com.aiguide.assistant.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 隐私蒙版管理器：通过 WindowManager 添加全屏黑色覆盖层，
 * 保护用户隐私，防止旁人窥屏。
 *
 * 自动绑定 [ServiceBus.privacyOverlayAlpha] StateFlow 同步透明度。
 */
@Singleton
class PrivacyOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceBus: ServiceBus
) {

    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var overlayView: View? = null
    private var isVisible = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val windowType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

    /**
     * 检查悬浮窗权限是否已授权。
     */
    fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

    /**
     * 显示全屏黑色蒙版。
     * @param initialAlpha 初始透明度 0-100，默认 100（完全不透明）
     * @return true 如果成功显示，false 如果无权限或已显示
     */
    fun show(initialAlpha: Int = 100): Boolean {
        if (!canDrawOverlays()) return false
        if (isVisible) return true
        val wm = windowManager ?: return false

        val view = View(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            alpha = initialAlpha.coerceIn(0, 100) / 100f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            wm.addView(view, params)
            overlayView = view
            isVisible = true
            startObservingAlpha()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 隐藏蒙版并从 WindowManager 移除。
     */
    fun hide() {
        val view = overlayView ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
            // View 已被移除或未添加
        }
        overlayView = null
        isVisible = false
    }

    /**
     * 蒙版当前是否可见。
     */
    fun isVisible(): Boolean = isVisible

    /**
     * 动态设置蒙版透明度。
     * @param alpha 透明度 0（完全透明）~ 100（完全不透明）
     */
    fun setAlpha(alpha: Int) {
        val clamped = alpha.coerceIn(0, 100)
        overlayView?.alpha = clamped / 100f
        serviceBus.setPrivacyOverlayAlpha(clamped)
    }

    /**
     * 监听 ServiceBus.privacyOverlayAlpha 自动同步透明度。
     */
    private fun startObservingAlpha() {
        scope.launch {
            serviceBus.privacyOverlayAlpha.collectLatest { alpha ->
                overlayView?.alpha = alpha / 100f
            }
        }
    }
}
