package com.aiguide.assistant.engine

import android.content.Context
import com.aiguide.assistant.service.DeviceProfile as DeviceProfileLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备性能分级器：根据 CPU 核心数、RAM 大小、屏幕分辨率
 * 将设备分为 HIGH / MEDIUM / LOW 三档，供 [PerformanceOptimizer] 使用。
 *
 * ## 分级标准
 * - HIGH:   8+ 核 && 6GB+ RAM
 * - MEDIUM: 4-7 核 && 3-5GB RAM
 * - LOW:    <=3 核 || <3GB RAM
 */
@Singleton
class DeviceProfile @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** HIGH 档最低核心数 */
        private const val HIGH_MIN_CORES = 8

        /** HIGH 档最低 RAM（字节） */
        private const val HIGH_MIN_RAM_BYTES = 6L * 1024 * 1024 * 1024  // 6 GB

        /** MEDIUM 档最低核心数 */
        private const val MEDIUM_MIN_CORES = 4

        /** MEDIUM 档最低 RAM（字节） */
        private const val MEDIUM_MIN_RAM_BYTES = 3L * 1024 * 1024 * 1024  // 3 GB
    }

    /** 当前设备档位（初始化后不变） */
    val profile: DeviceProfileLevel = detect()

    /**
     * 检测当前设备性能档位。
     */
    private fun detect(): DeviceProfileLevel {
        val cores = Runtime.getRuntime().availableProcessors()
        val ramBytes = getTotalRamBytes()

        return when {
            cores >= HIGH_MIN_CORES && ramBytes >= HIGH_MIN_RAM_BYTES -> DeviceProfileLevel.HIGH
            cores >= MEDIUM_MIN_CORES && ramBytes >= MEDIUM_MIN_RAM_BYTES -> DeviceProfileLevel.MEDIUM
            else -> DeviceProfileLevel.LOW
        }
    }

    /**
     * 读取设备总 RAM 大小（字节）。
     */
    private fun getTotalRamBytes(): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mi.totalMem
        } catch (_: Exception) {
            MEDIUM_MIN_RAM_BYTES - 1
        }
    }
}
