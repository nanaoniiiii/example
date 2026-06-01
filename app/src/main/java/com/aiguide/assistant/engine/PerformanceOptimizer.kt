package com.aiguide.assistant.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.aiguide.assistant.service.DeviceProfile as DeviceProfileLevel
import com.aiguide.assistant.service.PerformanceParams
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自适应性能调优器：根据 [DeviceProfile] 档位和电池状态
 * 动态调整各项引擎参数，通过 ServiceBus 发布 [PerformanceParams]。
 *
 * ## 档位参数
 * | 参数          | HIGH                | MEDIUM              | LOW              |
 * |--------------|---------------------|---------------------|------------------|
 * | frameSkip    | 1 (每帧推理)         | 3 (每 3 帧推理)      | 5 (每 5 帧推理)   |
 * | skipInterval | 1                   | 3                   | 5                |
 * | flashBattery | 4000ms              | 8000ms              | 关闭(0)           |
 * | flashCharging| 8000ms              | 12000ms             | 关闭(0)           |
 * | 唤醒词        | 始终在线             | 始终在线             | 按需             |
 *
 * ## 电池状态
 * - 电量 < 15% → 自动切换到 LOW 档参数，batteryWarning = true
 */
@Singleton
class PerformanceOptimizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceProfile: DeviceProfile,
    private val serviceBus: ServiceBus
) {

    companion object {
        /** 低电量阈值（%） */
        private const val LOW_BATTERY_THRESHOLD = 15

        /** 电池状态轮询间隔（毫秒） */
        private const val BATTERY_POLL_INTERVAL_MS = 30_000L  // 30 秒
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // 初始化参数
        updateParams()

        // 发布设备档位到 ServiceBus（将 engine.DeviceProfile.profile 枚举值发布）
        serviceBus.deviceProfile.value = deviceProfile.profile

        // 启动电池状态轮询
        startBatteryMonitor()
    }

    // ========================
    // 参数计算
    // ========================

    /**
     * 根据设备档位和电池状态，计算当前最优 PerformanceParams。
     */
    private fun updateParams() {
        val batteryLevel = getBatteryLevel()
        val isLowBattery = batteryLevel in 0..LOW_BATTERY_THRESHOLD

        // 低电量强制 LOW 档
        val effectiveProfile = if (isLowBattery) {
            DeviceProfileLevel.LOW
        } else {
            deviceProfile.profile
        }

        val params = when (effectiveProfile) {
            DeviceProfileLevel.HIGH -> PerformanceParams(
                frameSkip = 1,
                skipInterval = 1,
                flashInterval = 4000L,
                batteryWarning = isLowBattery
            )
            DeviceProfileLevel.MEDIUM -> PerformanceParams(
                frameSkip = 3,
                skipInterval = 3,
                flashInterval = 8000L,
                batteryWarning = isLowBattery
            )
            DeviceProfileLevel.LOW -> PerformanceParams(
                frameSkip = 5,
                skipInterval = 5,
                flashInterval = 0L,  // 关闭闪光灯
                batteryWarning = isLowBattery
            )
        }

        serviceBus.performanceParams.value = params
    }

    // ========================
    // 电池监控
    // ========================

    /**
     * 启动电池状态周期性轮询。
     */
    private fun startBatteryMonitor() {
        scope.launch {
            while (true) {
                updateParams()
                delay(BATTERY_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * 获取当前电池电量百分比。
     */
    private fun getBatteryLevel(): Int {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter) ?: return 100
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) 100
            else (level * 100 / scale)
        } catch (_: Exception) {
            100
        }
    }
}
