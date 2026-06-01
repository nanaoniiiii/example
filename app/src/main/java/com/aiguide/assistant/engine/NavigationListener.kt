package com.aiguide.assistant.engine

import com.aiguide.assistant.service.ServiceBus
import javax.inject.Inject
import javax.inject.Singleton

// ========================
// 导航指令密封类
// ========================

/**
 * 结构化导航指令 — 由导航 TTS 播报文本解析得到。
 *
 * @param distance 距离（米），无距离信息时为 0
 * @param roadName 路名（如有），无则为空字符串
 * @param raw      原始 TTS 播报原文
 */
sealed class NavInstruction(
    open val distance: Int,
    open val roadName: String,
    open val raw: String
) {
    data class TurnLeft(
        override val distance: Int,
        override val roadName: String,
        override val raw: String
    ) : NavInstruction(distance, roadName, raw)

    data class TurnRight(
        override val distance: Int,
        override val roadName: String,
        override val raw: String
    ) : NavInstruction(distance, roadName, raw)

    data class GoStraight(
        override val distance: Int,
        override val roadName: String,
        override val raw: String
    ) : NavInstruction(distance, roadName, raw)

    data class UTurn(
        override val distance: Int,
        override val roadName: String,
        override val raw: String
    ) : NavInstruction(distance, roadName, raw)

    data class Merge(
        override val distance: Int,
        override val roadName: String,
        override val raw: String
    ) : NavInstruction(distance, roadName, raw)

    data class Roundabout(
        override val distance: Int,
        override val roadName: String,
        override val raw: String
    ) : NavInstruction(distance, roadName, raw)

    /**
     * 无法匹配到已知指令时的兜底类型。
     */
    data class Unknown(
        override val distance: Int,
        override val roadName: String,
        override val raw: String
    ) : NavInstruction(distance, roadName, raw)
}

// ========================
// NavigationListener
// ========================

/**
 * 导航播报监听器：通过 AccessibilityService 捕获主流导航 App 的 TTS 播报文本，
 * 正则解析为结构化 [NavInstruction] 并发布到 [ServiceBus.navigationEvent]。
 *
 * ## 使用
 * 1. AccessibilityService 检测到 TTS 事件时调用 [onTtsEvent]
 * 2. 调用 [startListening] 指定目标导航包名
 * 3. 匹配的播报文本自动解析发布到总线
 *
 * ## 支持的导航 App
 * 高德地图 / 百度地图 / 腾讯地图
 */
@Singleton
class NavigationListener @Inject constructor(
    private val serviceBus: ServiceBus
) {

    companion object {
        /** 白名单导航包名 */
        val NAVIGATION_APPS: Set<String> = setOf(
            "com.autonavi.minimap",  // 高德地图
            "com.baidu.BaiduMap",    // 百度地图
            "com.tencent.map"        // 腾讯地图
        )

        // ------------------------------------------------------------
        // 正则解析：按优先级顺序依次匹配
        // ------------------------------------------------------------

        /** 距离提取：匹配 "XXX米" */
        private val DISTANCE_REGEX = Regex("""(\d+)\s*米""")

        /** 路名提取：匹配 "进入XXX路/街/大道"、"驶入XXX"、"上XXX" */
        private val ROAD_NAME_REGEX = Regex("""(?:进入|驶入|上|前方)\s*(\S{1,10}(?:路|街|大道|道|桥|隧道|匝道|环岛))""")

        /** 左转 */
        private val TURN_LEFT_REGEX = Regex("""左转|向左|靠左|左拐""")

        /** 右转 */
        private val TURN_RIGHT_REGEX = Regex("""右转|向右|靠右|右拐""")

        /** 直行 */
        private val GO_STRAIGHT_REGEX = Regex("""直行|沿当前|继续行驶|保持直行""")

        /** 掉头 */
        private val U_TURN_REGEX = Regex("""掉头|调头|U型""")

        /** 并道 / 匝道 */
        private val MERGE_REGEX = Regex("""并道|匝道|合流|靠右进入|靠左进入""")

        /** 环岛 */
        private val ROUNDABOUT_REGEX = Regex("""环岛|转盘""")
    }

    private var isListening: Boolean = false
    private var targetPackage: String = ""

    // ========================
    // 公开 API
    // ========================

    fun startListening(packageName: String = "") {
        targetPackage = packageName
        isListening = true
    }

    fun stopListening() {
        isListening = false
        targetPackage = ""
    }

    fun isActive(): Boolean = isListening

    fun isNavigationApp(packageName: String): Boolean = packageName in NAVIGATION_APPS

    /**
     * 由 AccessibilityService 调用的 TTS 事件入口。
     * 仅当正在监听且包名匹配时才解析并发布。
     */
    fun onTtsEvent(packageName: String, text: String) {
        if (!isListening || text.isBlank()) return

        val shouldPublish = when {
            targetPackage.isNotEmpty() -> packageName == targetPackage
            else -> packageName in NAVIGATION_APPS
        }

        if (shouldPublish) {
            val instruction = parseInstruction(text)
            serviceBus.navigationEvent.tryEmit(instruction)
        }
    }

    // ========================
    // 解析逻辑
    // ========================

    /**
     * 将 TTS 播报原文解析为结构化 [NavInstruction]。
     *
     * 示例：
     * - "前方200米左转"        → TurnLeft(200, "", raw)
     * - "前方500米右转进入中山路" → TurnRight(500, "中山路", raw)
     * - "请掉头行驶"            → UTurn(0, "", raw)
     * - "前方进入环岛"           → Roundabout(0, "", raw)
     */
    fun parseInstruction(text: String): NavInstruction {
        val distance = extractDistance(text)
        val roadName = extractRoadName(text)

        return when {
            MERGE_REGEX.containsMatchIn(text)      -> NavInstruction.Merge(distance, roadName, text)
            ROUNDABOUT_REGEX.containsMatchIn(text) -> NavInstruction.Roundabout(distance, roadName, text)
            U_TURN_REGEX.containsMatchIn(text)     -> NavInstruction.UTurn(distance, roadName, text)
            TURN_LEFT_REGEX.containsMatchIn(text)  -> NavInstruction.TurnLeft(distance, roadName, text)
            TURN_RIGHT_REGEX.containsMatchIn(text) -> NavInstruction.TurnRight(distance, roadName, text)
            GO_STRAIGHT_REGEX.containsMatchIn(text) -> NavInstruction.GoStraight(distance, roadName, text)
            else -> extractSecondaryMatch(text, distance, roadName)
        }
    }

    /**
     * 次要匹配：处理优先级较低或需要更细致判断的指令。
     */
    private fun extractSecondaryMatch(text: String, distance: Int, roadName: String): NavInstruction {
        return when {
            text.contains("左")  -> NavInstruction.TurnLeft(distance, roadName, text)
            text.contains("右")  -> NavInstruction.TurnRight(distance, roadName, text)
            text.contains("直")  -> NavInstruction.GoStraight(distance, roadName, text)
            else                 -> NavInstruction.Unknown(distance, roadName, text)
        }
    }

    /**
     * 从文本中提取距离（米）。
     */
    private fun extractDistance(text: String): Int {
        val match = DISTANCE_REGEX.find(text) ?: return 0
        return match.groupValues[1].toIntOrNull() ?: 0
    }

    /**
     * 从文本中提取路名。
     */
    private fun extractRoadName(text: String): String {
        val match = ROAD_NAME_REGEX.find(text) ?: return ""
        return match.groupValues[1]
    }
}
