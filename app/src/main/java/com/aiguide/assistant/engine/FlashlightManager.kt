package com.aiguide.assistant.engine

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.Sensor
import android.hardware.SensorManager
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
 * 3. 天黑状态下周期性闪烁闪光灯
 * 4. 闪烁间隔由 [ServiceBus.performanceParams.flashInterval] 动态控制
 *    - flashInterval = 0 → 关闭闪光灯
 *    - 否则按指定间隔闪烁
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

        /** 闪光灯点亮时长（毫秒） */
        private const val FLASH_ON_DURATION_MS = 300L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    /** 后置摄像头 ID（带闪光灯） */
    private var rearCameraId: String? = null
    private var lightSensorAvailable: Boolean = false

    private var isRunning: Boolean = false
    private var isFlashing: Boolean = false

    /** Phase 4: 动态闪烁间隔（由 performanceParams.flashInterval 控制） */
    private var currentFlashInterval: Long = 8000L

    private var flashRunnable: Runnable? = null

    init {
        try {
            detectCameraWithFlash()
        } catch (_: Exception) {
            // 摄像头检测失败，静默跳过
        }
        observeEnvironmentLight()
        observePerformanceParams()
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
        val cm = cameraManager ?: run {
            lightSensorAvailable = false
            return
        }
        try {
            for (cameraId in cm.cameraIdList) {
                val characteristics = cm.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val flashAvailable =
                        characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

                    // 通过 SensorManager 检测环境光 Sensor 是否可用
                    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                    lightSensorAvailable = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) != null

                    if (flashAvailable) {
                        rearCameraId = cameraId
                    }
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
    // 性能参数监听（Phase 4）
    // ========================

    /**
     * 监听 performanceParams 动态更新闪光灯间隔。
     */
    private fun observePerformanceParams() {
        scope.launch {
            serviceBus.performanceParams.collectLatest { params ->
                currentFlashInterval = params.flashInterval

                // flashInterval = 0 表示关闭闪光灯
                if (currentFlashInterval <= 0L && isFlashing) {
                    cancelFlashRunnable()
                    turnOffTorch()
                    serviceBus.flashLightEnabled.value = false
                } else if (currentFlashInterval > 0L && isRunning && !isFlashing &&
                    serviceBus.isDarkEnvironment.value) {
                    startFlashing()
                }
            }
        }
    }

    // ========================
    // 闪光灯控制
    // ========================

    /**
     * 开始周期性闪烁。
     */
    private fun startFlashing() {
        if (rearCameraId == null || currentFlashInterval <= 0L) return
        scheduleFlash()
    }

    /**
     * 调度一次"亮 + 关 + 延迟"循环。
     */
    private fun scheduleFlash() {
        if (!isRunning || currentFlashInterval <= 0L) return

        val interval = currentFlashInterval

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
                cameraManager?.setTorchMode(cameraId, true)
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
                cameraManager?.setTorchMode(cameraId, false)
            }
        } catch (_: Exception) {
            // 忽略
        }
    }
}
