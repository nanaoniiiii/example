package com.aiguide.assistant.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
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
 * 天黑闪光灯提醒管理器：利用环境光 Sensor 判定天黑状态，
 * 在天黑时周期性闪烁手电筒以提醒周围车辆/行人。
 *
 * ## 工作机制
 * 1. 通过 CameraManager + CameraCharacteristics 读取环境光 Sensor (LIGHT)
 * 2. 环境光 < 10 lux → 判定为天黑
 * 3. 天黑状态下周期性闪烁闪光灯（开 300ms → 关）
 * 4. 电源模式自适应：充电时 4 秒间隔，电池模式 8 秒间隔
 *
 * ## 用法
 * ```kotlin
 * flashlightManager.start()
 * // ... 导航中 ...
 * flashlightManager.stop()
 * ```
 */
@Singleton
class FlashlightManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceBus: ServiceBus
) {

    companion object {
        /** 天黑判定阈值（lux） */
        private const val DARK_THRESHOLD_LUX = 10f

        /** 电池模式下闪烁间隔（毫秒） */
        private const val FLASH_INTERVAL_BATTERY_MS = 8000L

        /** 充电模式下闪烁间隔（毫秒） */
        private const val FLASH_INTERVAL_CHARGING_MS = 4000L

        /** 闪光灯点亮时长（毫秒） */
        private const val FLASH_ON_DURATION_MS = 300L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** 后置摄像头 ID（带闪光灯） */
    private var rearCameraId: String? = null
    private var lightSensorAvailable: Boolean = false

    private var isRunning: Boolean = false
    private var isFlashing: Boolean = false

    private var flashRunnable: Runnable? = null

    init {
        detectCameraWithFlash()
        observeEnvironmentLight()
        observePowerState()
    }

    // ========================
    // 公开 API
    // ========================

    /**
     * 启动天黑检测与闪光灯提醒。
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        // 环境光已在 init 中持续监听，start 后开启闪光逻辑
    }

    /**
     * 停止闪光灯提醒并关闭手电筒。
     */
    fun stop() {
        isRunning = false
        cancelFlashRunnable()
        turnOffTorch()
        serviceBus.flashLightEnabled.value = false
    }

    /**
     * 当前是否正在闪烁。
     */
    fun isFlashing(): Boolean = isFlashing

    // ========================
    // 摄像头检测
    // ========================

    /**
     * 检测后置摄像头是否支持手电筒 + 环境光 Sensor。
     */
    private fun detectCameraWithFlash() {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val flashAvailable =
                        characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

                    // 检测环境光 Sensor
                    val lightSensorKeys = characteristics.get(
                        CameraCharacteristics.SENSOR_INFO_LIGHT_AVAILABLE
                    )

                    if (flashAvailable) {
                        rearCameraId = cameraId
                    }

                    lightSensorAvailable = lightSensorKeys ?: false
                    break
                }
            }
        } catch (_: Exception) {
            lightSensorAvailable = false
        }
    }

    // ========================
    // 环境光监听
    // ========================

    /**
     * 持续读取环境光 lux 状态，判定天黑。
     */
    private fun observeEnvironmentLight() {
        scope.launch {
            serviceBus.environmentLux.collectLatest { lux ->
                val isDark = lux < DARK_THRESHOLD_LUX && lightSensorAvailable
                serviceBus.isDarkEnvironment.value = isDark

                if (isDark && isRunning && !isFlashing) {
                    startFlashing()
                } else if (!isDark || !isRunning) {
                    cancelFlashRunnable()
                    turnOffTorch()
                    serviceBus.flashLightEnabled.value = false
                }
            }
        }
    }

    // ========================
    // 电源状态监听
    // ========================

    /**
     * 监听充电状态以自适应闪烁间隔。
     */
    private fun observePowerState() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }

        context.registerReceiver(null, filter)?.let { intent ->
            updatePowerState(intent)
        }
    }

    private fun updatePowerState(intent: Intent) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // 电源状态变化时，如果正在闪烁，重新调度以使用正确间隔
        if (isFlashing) {
            cancelFlashRunnable()
            startFlashing()
        }
    }

    private fun isCurrentlyCharging(): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter) ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }

    // ========================
    // 闪光灯控制
    // ========================

    /**
     * 开始周期性闪烁。
     */
    private fun startFlashing() {
        if (rearCameraId == null) return
        scheduleFlash()
    }

    /**
     * 调度一次"亮 + 关 + 延迟"循环。
     */
    private fun scheduleFlash() {
        if (!isRunning) return

        val interval = if (isCurrentlyCharging()) {
            FLASH_INTERVAL_CHARGING_MS
        } else {
            FLASH_INTERVAL_BATTERY_MS
        }

        flashRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                // 亮
                turnOnTorch()
                isFlashing = true
                serviceBus.flashLightEnabled.value = true

                // 300ms 后关
                mainHandler.postDelayed({
                    turnOffTorch()
                    isFlashing = false
                    serviceBus.flashLightEnabled.value = false
                }, FLASH_ON_DURATION_MS)

                // 下一个周期
                if (isRunning) {
                    mainHandler.postDelayed(this, interval)
                }
            }
        }

        // 立即执行第一次闪烁
        flashRunnable?.run()
    }

    /**
     * 取消闪烁调度。
     */
    private fun cancelFlashRunnable() {
        flashRunnable?.let { mainHandler.removeCallbacks(it) }
        flashRunnable = null
        isFlashing = false
    }

    /**
     * 打开手电筒。
     */
    private fun turnOnTorch() {
        try {
            rearCameraId?.let { cameraId ->
                cameraManager.setTorchMode(cameraId, true)
            }
        } catch (_: Exception) {
            // 手电筒不可用（被占用等）
        }
    }

    /**
     * 关闭手电筒。
     */
    private fun turnOffTorch() {
        try {
            rearCameraId?.let { cameraId ->
                cameraManager.setTorchMode(cameraId, false)
            }
        } catch (_: Exception) {
            // 忽略
        }
    }
}
