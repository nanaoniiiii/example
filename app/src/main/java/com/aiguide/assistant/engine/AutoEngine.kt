package com.aiguide.assistant.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.aiguide.assistant.service.AIGuideAccessibilityService
import com.aiguide.assistant.service.AutoActionResult
import com.aiguide.assistant.service.ServiceBus
import com.aiguide.assistant.service.TtsPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

// ========================
// UI 操作封装
// ========================

/** 半自动手机操作的原子 UI 操作，由 AutoEngine 解析语音指令后生成。 */
sealed class UiAction {
    /** 点击屏幕坐标 (x, y)，targetText 为延迟解析的目标文本 */
    data class Click(val x: Int, val y: Int, val targetText: String? = null) : UiAction()

    /** 滑动手势：从 (x1, y1) 到 (x2, y2)，duration 毫秒 */
    data class Swipe(
        val x1: Int, val y1: Int,
        val x2: Int, val y2: Int,
        val duration: Long = 300L
    ) : UiAction()

    /** 向当前焦点输入框输入文字 */
    data class InputText(val text: String) : UiAction()

    /** 系统返回键 */
    object Back : UiAction()

    /** 回到桌面 */
    object Home : UiAction()

    /** 打开最近任务 */
    object Recent : UiAction()

    /** 下拉通知栏 */
    object Notifications : UiAction()
}

// ========================
// AutoEngine — 半自动手机操作引擎
// ========================

/**
 * 半自动手机操作引擎。
 *
 * ## 工作流
 * 1. 监听 [ServiceBus.voiceCommand] 语音指令
 * 2. 解析为 [UiAction] 操作序列
 * 3. 通过 [AIGuideAccessibilityService] 查找目标节点（AccessibilityNodeInfo）
 * 4. 查找失败时回退到 OCR（VisionEngine 帧分析）定位文字区域
 * 5. 普通指令通过 TTS 播报后执行；敏感操作（支付/授权/删除）强制语音确认
 * 6. 结果通过 [ServiceBus.autoActionResult] 发布
 *
 * ## 状态守卫
 * - [SafetyGuard] 通过 [ServiceBus.autoEngineEnabled] 控制引擎启停
 * - 危险警报、ASSIST_ACTIVE 模式、摄像头关闭时自动暂停
 */
@Singleton
class AutoEngine @Inject constructor(
    private val serviceBus: ServiceBus,
    private val ttsManager: TtsManager,
    private val visionEngine: VisionEngine
) {

    companion object {
        /** 敏感操作关键词：匹配到即触发语音确认 */
        private val SENSITIVE_KEYWORDS = setOf(
            "支付", "付款", "购买", "转账", "充值", "扣款", "免密",
            "授权", "验证码", "密码", "登录", "身份",
            "删除", "清空", "卸载", "移除", "格式化"
        )

        /** 确认超时（毫秒） */
        private const val CONFIRMATION_TIMEOUT_MS = 8000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var confirmationJob: Job? = null

    @Volatile
    private var pendingAction: UiAction? = null

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting

    init {
        scope.launch {
            serviceBus.voiceCommand.collect { command ->
                if (canExecute()) processCommand(command)
            }
        }
    }

    // ---------- 公开 API ----------

    /** 检查引擎是否允许执行操作（由 SafetyGuard 控制） */
    fun canExecute(): Boolean = serviceBus.autoEngineEnabled.value

    /** 取消当前等待中的确认 */
    fun dismissConfirmation() {
        confirmationJob?.cancel()
        pendingAction = null
        serviceBus.requestTts("操作已取消", TtsPriority.NORMAL)
    }

    // ---------- 指令处理 ----------

    /**
     * 解析语音指令为操作序列并执行。
     */
    fun processCommand(command: String) {
        val actions = resolveActions(command)
        if (actions.isEmpty()) {
            serviceBus.emitAutoActionResult(
                AutoActionResult(
                    actionType = "PARSE",
                    actionDetail = command,
                    success = false,
                    message = "无法解析指令为操作序列"
                )
            )
            return
        }
        executeSequence(actions)
    }

    // ========================
    // 阶段 1：指令解析 → UiAction 序列
    // ========================

    private fun resolveActions(cmd: String): List<UiAction> {
        val c = cmd.trim()
        return when {
            // 滑动指令
            c.matches(Regex(".*(上[滑划]|往[上]).*")) ->
                listOf(UiAction.Swipe(540, 1500, 540, 500))

            c.matches(Regex(".*(下[滑划]|往[下]).*")) ->
                listOf(UiAction.Swipe(540, 500, 540, 1500))

            c.matches(Regex(".*(左[滑划]|往[左]).*")) ->
                listOf(UiAction.Swipe(900, 1000, 200, 1000))

            c.matches(Regex(".*(右[滑划]|往[右]).*")) ->
                listOf(UiAction.Swipe(200, 1000, 900, 1000))

            // 系统导航
            c.contains("返回") || c.contains("后退") ->
                listOf(UiAction.Back)

            c.contains("桌面") || c.contains("主屏幕") ->
                listOf(UiAction.Home)

            c.contains("最近") || c.contains("多任务") ->
                listOf(UiAction.Recent)

            c.contains("通知栏") ->
                listOf(UiAction.Notifications)

            // 输入文字
            c.contains("输入") -> {
                val text = c.substringAfter("输入").trim()
                if (text.isNotEmpty()) listOf(UiAction.InputText(text)) else emptyList()
            }

            // 点击目标：优先无障碍节点查找，失败回退 OCR
            else -> {
                val target = extractClickTarget(c)
                if (target != null) {
                    // 阶段 1 不解析坐标，延后到执行时解析（允许屏幕变化后的最新状态）
                    listOf(UiAction.Click(-1, -1).copyWithTarget(target))
                } else {
                    emptyList()
                }
            }
        }
    }

    /**
     * 从指令中提取点击目标文本。
     * "点击微信" → "微信"; "打开设置" → "设置"; "微信" → "微信"
     */
    private fun extractClickTarget(cmd: String): String? {
        val prefixes = listOf("点击", "按", "打开", "进入", "启动", "到")
        for (p in prefixes) {
            if (cmd.startsWith(p)) {
                val tail = cmd.removePrefix(p).trim()
                if (tail.isNotEmpty()) return tail
            }
        }
        // 无前缀，整句 ≤10 字视为目标
        return cmd.takeIf { it.length in 1..10 }
    }

    // ========================
    // 阶段 2：目标坐标解析
    // ========================

    /**
     * 通过 AccessibilityNodeInfo 查找目标文本节点，返回屏幕中心坐标。
     * 查找失败返回 null，上层回退到 OCR。
     */
    private fun resolveTargetCoordinates(targetText: String): Pair<Int, Int>? {
        val service = AIGuideAccessibilityService.instance ?: return null

        // 1) 无障碍节点树精确匹配
        val root = service.rootInActiveWindow ?: return null
        val exactNodes = root.findAccessibilityNodeInfosByText(targetText)
        if (exactNodes.isNotEmpty()) {
            val rect = Rect()
            exactNodes[0].getBoundsInScreen(rect)
            exactNodes.forEach { it.recycle() }
            return Pair(rect.centerX(), rect.centerY())
        }

        // 2) 遍历子节点模糊匹配
        val found = findNodeByTextRecursive(root, targetText)
        root.recycle()

        if (found != null) {
            val rect = Rect()
            found.getBoundsInScreen(rect)
            found.recycle()
            return Pair(rect.centerX(), rect.centerY())
        }

        // 3) OCR 回退 — 从 VisionEngine 最近的帧中 OCR 定位
        return resolveViaOcr(targetText)
    }

    /** 递归搜索 AccessibilityNodeInfo 树，匹配包含 targetText 的节点 */
    private fun findNodeByTextRecursive(
        node: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text) == true && node.isClickable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByTextRecursive(child, text)
            if (found != null) return found
        }
        return null
    }

    /**
     * OCR 回退：从 VisionEngine 相邻帧中识别文字并定位坐标。
     * 当前为框架实现，实际 OCR 需集成 ML Kit Text Recognition 或 Tesseract。
     *
     * TODO: 集成 ML Kit → 传入 ImageProxy → 匹配 targetText → 返回 BoundingBox.center
     */
    private fun resolveViaOcr(targetText: String): Pair<Int, Int>? {
        // VisionEngine 通过 ServiceBus.cameraFrame 持续输出 ImageProxy YUV 帧。
        // 实际 OCR 集成路径：
        //   val frame = serviceBus.cameraFrame.replayCache.lastOrNull() ?: return null
        //   val inputImage = InputImage.fromMediaImage(frame.image!!, frame.imageInfo.rotationDegrees)
        //   val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        //   val result = recognizer.process(inputImage).await()
        //   for (block in result.textBlocks) {
        //       if (block.text.contains(targetText)) {
        //           frame.close()
        //           return Pair(block.boundingBox!!.centerX(), block.boundingBox!!.centerY())
        //       }
        //   }
        //   frame.close()
        return null
    }

    // ========================
    // 阶段 3：操作执行 → AccessibilityService 手势
    // ========================

    private fun executeSequence(actions: List<UiAction>) {
        if (!canExecute()) return
        scope.launch {
            _isExecuting.value = true
            try {
                for (action in actions) {
                    if (!canExecute()) break
                    executeAction(action)
                    delay(500L)
                }
            } finally {
                _isExecuting.value = false
            }
        }
    }

    private suspend fun executeAction(action: UiAction) {
        // 若是延迟解析的点击目标，现在解析坐标
        val resolvedAction = if (action is UiAction.Click && action.x == -1 && action.y == -1) {
            val target = action.targetText
            if (target != null) {
                val coords = resolveTargetCoordinates(target)
                if (coords != null) UiAction.Click(coords.first, coords.second)
                else {
                    serviceBus.emitAutoActionResult(
                        AutoActionResult("CLICK", "目标「$target」",
                            false, "未找到目标节点")
                    )
                    serviceBus.requestTts("找不到「$target」", TtsPriority.HIGH)
                    return
                }
            } else return
        } else action

        val isSensitive = isSensitiveAction(resolvedAction)

        if (isSensitive) {
            val confirmed = requestConfirmation(resolvedAction)
            if (!confirmed) {
                serviceBus.emitAutoActionResult(
                    AutoActionResult(
                        actionTypeName(resolvedAction),
                        describeAction(resolvedAction),
                        false,
                        "用户未确认敏感操作"
                    )
                )
                return
            }
        } else {
            announceAction(resolvedAction)
        }

        val result = performAction(resolvedAction)
        serviceBus.emitAutoActionResult(result)

        if (!result.success) {
            serviceBus.requestTts("操作失败：${result.message}", TtsPriority.HIGH)
        }
    }

    // ---------- 敏感操作确认 ----------

    private fun isSensitiveAction(action: UiAction): Boolean {
        if (action is UiAction.InputText) {
            return SENSITIVE_KEYWORDS.any { action.text.contains(it) }
        }
        return false
    }

    private suspend fun requestConfirmation(action: UiAction): Boolean {
        val desc = describeAction(action)
        serviceBus.requestTts("即将${desc}，确认请说确认", TtsPriority.CRITICAL)
        pendingAction = action

        return try {
            withTimeout(CONFIRMATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    confirmationJob = scope.launch {
                        serviceBus.voiceCommand.collect { cmd ->
                            val lower = cmd.trim().lowercase()
                            if (lower.contains("确认") || lower.contains("好的") ||
                                lower.contains("可以") || lower.contains("是") ||
                                lower.contains("行") || lower.contains("对")
                            ) {
                                if (cont.isActive) cont.resume(true)
                                confirmationJob?.cancel()
                            } else if (lower.contains("取消") || lower.contains("不") ||
                                lower.contains("别") || lower.contains("否")
                            ) {
                                if (cont.isActive) cont.resume(false)
                                confirmationJob?.cancel()
                            }
                        }
                    }
                    cont.invokeOnCancellation { confirmationJob?.cancel() }
                }
            }
        } catch (_: TimeoutCancellationException) {
            serviceBus.requestTts("确认超时，操作已取消", TtsPriority.HIGH)
            false
        } finally {
            pendingAction = null
            confirmationJob?.cancel()
        }
    }

    private fun announceAction(action: UiAction) {
        serviceBus.requestTts("即将${describeAction(action)}", TtsPriority.HIGH)
    }

    // ---------- 手势执行 ----------

    private fun performAction(action: UiAction): AutoActionResult {
        val service = AIGuideAccessibilityService.instance
        if (service == null) {
            return AutoActionResult(
                actionTypeName(action), describeAction(action),
                false, "无障碍服务未连接"
            )
        }

        return try {
            when (action) {
                is UiAction.Click -> {
                    service.performClick(action.x, action.y)
                    AutoActionResult("CLICK", "(${action.x},${action.y})", true)
                }
                is UiAction.Swipe -> {
                    service.performSwipe(action.x1, action.y1, action.x2, action.y2, action.duration)
                    AutoActionResult("SWIPE",
                        "(${action.x1},${action.y1})→(${action.x2},${action.y2})", true)
                }
                is UiAction.InputText -> {
                    service.performTextInput(action.text)
                    AutoActionResult("INPUT_TEXT", action.text, true)
                }
                is UiAction.Back -> {
                    service.performBack()
                    AutoActionResult("BACK", "", true)
                }
                is UiAction.Home -> {
                    service.performHome()
                    AutoActionResult("HOME", "", true)
                }
                is UiAction.Recent -> {
                    service.performRecent()
                    AutoActionResult("RECENT", "", true)
                }
                is UiAction.Notifications -> {
                    service.performNotifications()
                    AutoActionResult("NOTIFICATIONS", "", true)
                }
            }
        } catch (e: Exception) {
            AutoActionResult(
                actionTypeName(action), describeAction(action),
                false, "执行失败: ${e.message}"
            )
        }
    }

    // ---------- 工具方法 ----------

    private fun describeAction(action: UiAction): String = when (action) {
        is UiAction.Click -> "点击坐标(${action.x},${action.y})"
        is UiAction.Swipe -> "从(${action.x1},${action.y1})滑到(${action.x2},${action.y2})"
        is UiAction.InputText -> "输入文字「${action.text}」"
        is UiAction.Back -> "返回"
        is UiAction.Home -> "回到桌面"
        is UiAction.Recent -> "打开最近任务"
        is UiAction.Notifications -> "下拉通知栏"
    }

    private fun actionTypeName(action: UiAction): String = when (action) {
        is UiAction.Click -> "CLICK"
        is UiAction.Swipe -> "SWIPE"
        is UiAction.InputText -> "INPUT_TEXT"
        is UiAction.Back -> "BACK"
        is UiAction.Home -> "HOME"
        is UiAction.Recent -> "RECENT"
        is UiAction.Notifications -> "NOTIFICATIONS"
    }
}

// ========================
// 内部辅助：延迟解析点击目标
// ========================

/** 扩展：为 Click(-1,-1) 注入目标文本 */
private fun UiAction.Click.copyWithTarget(target: String): UiAction.Click {
    return copy(targetText = target)
}
