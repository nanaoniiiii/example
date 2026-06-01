package com.aiguide.assistant.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.aiguide.assistant.R
import com.aiguide.assistant.service.DeviceProfile
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 状态面板悬浮窗：通过 WindowManager TYPE_APPLICATION_OVERLAY 显示
 * 蒙版状态、摄像头状态、电池模式、设备档位。
 */
@Singleton
class StatusIndicator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceBus: ServiceBus
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var indicatorView: View? = null
    private var textOverlay: TextView? = null
    private var textCamera: TextView? = null
    private var textBattery: TextView? = null
    private var textDeviceTier: TextView? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val windowType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

    val isVisible: Boolean get() = indicatorView != null

    fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

    fun show(): Boolean {
        if (!canDrawOverlays()) return false
        if (indicatorView != null) return true

        val view = LayoutInflater.from(context).inflate(R.layout.item_status_indicator, null) as LinearLayout
        textOverlay = view.findViewById(R.id.tvOverlay)
        textCamera = view.findViewById(R.id.tvCamera)
        textBattery = view.findViewById(R.id.tvBattery)
        textDeviceTier = view.findViewById(R.id.tvDeviceTier)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dpToPx(8)
            y = dpToPx(80)
        }

        try {
            windowManager.addView(view, params)
            indicatorView = view
            serviceBus.statusIndicatorVisible.value = true
            startObserving()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun hide() {
        val view = indicatorView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) { }
        indicatorView = null
        serviceBus.statusIndicatorVisible.value = false
    }

    private fun startObserving() {
        // 合并监听蒙版透明度（>50 = 开）和 AssistMode
        scope.launch {
            combine(
                serviceBus.privacyOverlayAlpha,
                serviceBus.assistMode
            ) { alpha, mode ->
                val overlayOn = alpha > 50 || mode == com.aiguide.assistant.service.AssistMode.ACTIVE
                val label = if (overlayOn) "蒙版：开" else "蒙版：关"
                textOverlay?.text = label
                textOverlay?.setTextColor(
                    if (overlayOn) context.getColor(R.color.status_on)
                    else context.getColor(R.color.status_off)
                )
            }.collectLatest { }
        }

        // 监听摄像头状态
        scope.launch {
            serviceBus.cameraEnabled.collectLatest { enabled ->
                val label = if (enabled) "摄像头：开" else "摄像头：关"
                textCamera?.text = label
                textCamera?.setTextColor(
                    if (enabled) context.getColor(R.color.status_on)
                    else context.getColor(R.color.status_off)
                )
            }
        }

        // 监听电池状态（直接从系统读取）
        scope.launch {
            while (true) {
                val batteryStatus = getBatteryStatus()
                textBattery?.text = "电池：$batteryStatus"
                textBattery?.setTextColor(
                    when {
                        batteryStatus.contains("低电量") -> context.getColor(R.color.error)
                        batteryStatus.contains("充电") -> context.getColor(R.color.status_on)
                        else -> context.getColor(R.color.on_surface_variant)
                    }
                )
                kotlinx.coroutines.delay(5000)
            }
        }

        // 监听设备档位
        scope.launch {
            serviceBus.deviceProfile.collectLatest { profile ->
                val label = when (profile) {
                    DeviceProfile.HIGH -> "设备档位：HIGH"
                    DeviceProfile.MEDIUM -> "设备档位：MEDIUM"
                    DeviceProfile.LOW -> "设备档位：LOW"
                }
                textDeviceTier?.text = label
                textDeviceTier?.setTextColor(
                    when (profile) {
                        DeviceProfile.HIGH -> context.getColor(R.color.status_on)
                        DeviceProfile.MEDIUM -> context.getColor(R.color.status_warning)
                        DeviceProfile.LOW -> context.getColor(R.color.error)
                    }
                )
            }
        }
    }

    private fun getBatteryStatus(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        if (level < 0 || scale <= 0) return "未知"

        val pct = level * 100 / scale
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL

        return when {
            pct <= 15 -> "低电量 ${pct}%"
            isCharging -> "充电 ${pct}%"
            else -> "${pct}%"
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
