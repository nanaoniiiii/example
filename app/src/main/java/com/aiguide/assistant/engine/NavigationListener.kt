package com.aiguide.assistant.engine

import com.aiguide.assistant.service.ServiceBus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导航播报监听器：通过 AccessibilityService 捕获主流导航 App 的 TTS 播报文本，
 * 并发布到 [ServiceBus.navigationEvent]。
 *
 * ## 使用
 * 1. AccessibilityService 检测到 TTS 事件时调用 [onTtsEvent]
 * 2. 调用 [startListening] 指定目标导航包名
 * 3. 匹配的播报文本自动发布到总线
 *
 * ## 支持的导航 App
 * 高德地图 / 百度地图 / 腾讯地图 / Google Maps / Waze
 */
@Singleton
class NavigationListener @Inject constructor(
    private val serviceBus: ServiceBus
) {

    companion object {
        /** 主流导航 App 包名集合 */
        val NAVIGATION_APPS: Set<String> = setOf(
            "com.autonavi.minimap",          // 高德地图
            "com.baidu.BaiduMap",            // 百度地图
            "com.tencent.map",               // 腾讯地图
            "com.google.android.apps.maps",  // Google Maps
            "com.waze"                       // Waze
        )
    }

    private var isListening: Boolean = false
    private var targetPackage: String = ""

    /**
     * 开始监听指定导航应用的 TTS 播报。
     *
     * @param packageName 目标导航 App 包名，为空则监听所有已知导航 App
     */
    fun startListening(packageName: String = "") {
        targetPackage = packageName
        isListening = true
    }

    /**
     * 停止监听。
     */
    fun stopListening() {
        isListening = false
        targetPackage = ""
    }

    /**
     * 由 AccessibilityService 调用的 TTS 事件入口。
     * 仅当正在监听且包名匹配时才发布到总线。
     *
     * @param packageName 产生 TTS 播报的应用包名
     * @param text TTS 播报文本内容
     */
    fun onTtsEvent(packageName: String, text: String) {
        if (!isListening || text.isBlank()) return

        val shouldPublish = when {
            targetPackage.isNotEmpty() -> packageName == targetPackage
            else -> packageName in NAVIGATION_APPS
        }

        if (shouldPublish) {
            serviceBus.navigationEvent.tryEmit(text)
        }
    }

    /**
     * 是否处于活跃监听状态。
     */
    fun isActive(): Boolean = isListening

    /**
     * 判断给定包名是否为已知导航应用。
     */
    fun isNavigationApp(packageName: String): Boolean = packageName in NAVIGATION_APPS
}
