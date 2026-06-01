package com.aiguide.assistant.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiguide.assistant.R
import com.aiguide.assistant.engine.NavInstruction
import com.aiguide.assistant.service.AssistMode
import com.aiguide.assistant.service.AutoActionResult
import com.aiguide.assistant.service.DeviceProfile
import com.aiguide.assistant.service.HazardLevel
import com.aiguide.assistant.service.HazardResult
import com.aiguide.assistant.service.ServiceBus
import com.aiguide.assistant.service.TtsPriority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class TestPanelActivity : AppCompatActivity() {

    @Inject
    lateinit var serviceBus: ServiceBus

    private val eventLogAdapter = EventLogAdapter()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_panel)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "功能测试面板"
        }

        setupVoiceModule()
        setupVisionModule()
        setupOverlayModule()
        setupNavigationModule()
        setupAutoActionModule()
        setupFlashlightModule()
        setupAssistMode()
        setupDeviceStatus()
        setupEventLog()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ========================
    // 语音模块
    // ========================

    private fun setupVoiceModule() {
        findViewById<View>(R.id.btnTtsBroadcast).setOnClickListener {
            serviceBus.requestTts("测试播报 - AIGuide 功能测试面板", TtsPriority.NORMAL)
            addLog("TTS 播报 → NORMAL")
        }

        findViewById<View>(R.id.btnSimulateWake).setOnClickListener {
            serviceBus.onVoiceCommand("小助")
            addLog("语音唤醒 → 小助")
        }

        findViewById<View>(R.id.btnVoiceCommand).setOnClickListener {
            val input = EditText(this)
            input.hint = "输入语音指令文本"
            AlertDialog.Builder(this)
                .setTitle("语音指令")
                .setView(input)
                .setPositiveButton("发送") { _, _ ->
                    val text = input.text.toString().ifBlank { "测试语音指令" }
                    serviceBus.onVoiceCommand(text)
                    addLog("语音指令 → $text")
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    // ========================
    // 视觉模块
    // ========================

    private fun setupVisionModule() {
        findViewById<View>(R.id.btnCameraOn).setOnClickListener {
            serviceBus.cameraEnabled.value = true
            addLog("摄像头 → 开启")
        }

        findViewById<View>(R.id.btnCameraOff).setOnClickListener {
            serviceBus.cameraEnabled.value = false
            addLog("摄像头 → 关闭")
        }

        val tvLux = findViewById<TextView>(R.id.tvEnvironmentLux)
        lifecycleScope.launch {
            serviceBus.environmentLux.collectLatest { lux ->
                tvLux.text = "环境光: %.1f lux".format(lux)
            }
        }
    }

    // ========================
    // 蒙版模块
    // ========================

    private fun setupOverlayModule() {
        findViewById<View>(R.id.btnOverlayShow).setOnClickListener {
            serviceBus.setPrivacyOverlayAlpha(100)
            addLog("蒙版 → 显示 (alpha=100)")
        }

        findViewById<View>(R.id.btnOverlayHide).setOnClickListener {
            serviceBus.setPrivacyOverlayAlpha(0)
            addLog("蒙版 → 隐藏 (alpha=0)")
        }

        val tvLabel = findViewById<TextView>(R.id.tvOverlayAlphaLabel)
        val seekBar = findViewById<android.widget.SeekBar>(R.id.seekOverlayAlpha)

        lifecycleScope.launch {
            serviceBus.privacyOverlayAlpha.collectLatest { alpha ->
                seekBar.progress = alpha
                tvLabel.text = "透明度: $alpha"
            }
        }

        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    serviceBus.setPrivacyOverlayAlpha(progress)
                    tvLabel.text = "透明度: $progress"
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    // ========================
    // 导航模块
    // ========================

    private fun setupNavigationModule() {
        findViewById<View>(R.id.btnNavSimulate).setOnClickListener {
            val input = EditText(this)
            input.hint = "输入导航文本（如：前方200米左转）"
            AlertDialog.Builder(this)
                .setTitle("模拟导航")
                .setView(input)
                .setPositiveButton("发送") { _, _ ->
                    val text = input.text.toString().ifBlank { "前方200米左转" }
                    val instruction = NavInstruction.TurnLeft(200, "测试路", text)
                    serviceBus.navigationEvent.tryEmit(instruction)
                    addLog("导航事件 → $text")
                }
                .setNegativeButton("取消", null)
                .show()
        }

        findViewById<View>(R.id.btnHazardCritical).setOnClickListener {
            serviceBus.hazardAlert.tryEmit(
                HazardResult(HazardLevel.CRITICAL, "前方障碍物紧急制动", "障碍物")
            )
            addLog("危险预警 → CRITICAL: 前方障碍物")
        }

        findViewById<View>(R.id.btnHazardWarning).setOnClickListener {
            serviceBus.hazardAlert.tryEmit(
                HazardResult(HazardLevel.WARNING, "请注意右侧行人", "人")
            )
            addLog("危险预警 → WARNING: 右侧行人")
        }

        findViewById<View>(R.id.btnHazardInfo).setOnClickListener {
            serviceBus.hazardAlert.tryEmit(
                HazardResult(HazardLevel.INFO, "前方路口减速慢行", "路口")
            )
            addLog("危险预警 → INFO: 前方路口")
        }
    }

    // ========================
    // 自动操作模块
    // ========================

    private fun setupAutoActionModule() {
        findViewById<View>(R.id.btnAutoClick).setOnClickListener {
            serviceBus.emitAutoActionResult(
                AutoActionResult("CLICK", "点击测试按钮", true)
            )
            addLog("自动操作 → 点击")
        }

        findViewById<View>(R.id.btnAutoSwipe).setOnClickListener {
            serviceBus.emitAutoActionResult(
                AutoActionResult("SWIPE", "向上滑动", true)
            )
            addLog("自动操作 → 滑动")
        }

        findViewById<View>(R.id.btnAutoBack).setOnClickListener {
            serviceBus.emitAutoActionResult(
                AutoActionResult("BACK", "返回上一页", true)
            )
            addLog("自动操作 → 返回")
        }
    }

    // ========================
    // 闪光灯模块
    // ========================

    private fun setupFlashlightModule() {
        findViewById<View>(R.id.btnFlashOn).setOnClickListener {
            serviceBus.flashLightEnabled.value = true
            addLog("闪光灯 → 开")
        }

        findViewById<View>(R.id.btnFlashOff).setOnClickListener {
            serviceBus.flashLightEnabled.value = false
            addLog("闪光灯 → 关")
        }

        findViewById<View>(R.id.btnDarkEnv).setOnClickListener {
            serviceBus.environmentLux.value = 5f
            serviceBus.isDarkEnvironment.value = true
            addLog("天黑环境 → lux=5.0")
        }
    }

    // ========================
    // 协助模式
    // ========================

    private fun setupAssistMode() {
        val tvMode = findViewById<TextView>(R.id.tvAssistMode)

        lifecycleScope.launch {
            serviceBus.assistMode.collectLatest { mode ->
                tvMode.text = "当前: ${mode.name}"
            }
        }

        findViewById<View>(R.id.btnAssistModeToggle).setOnClickListener {
            val current = serviceBus.assistMode.value
            val next = when (current) {
                AssistMode.IDLE -> AssistMode.PASSIVE
                AssistMode.PASSIVE -> AssistMode.ACTIVE
                AssistMode.ACTIVE -> AssistMode.IDLE
            }
            serviceBus.setAssistMode(next)
            addLog("协助模式 → ${next.name}")
        }
    }

    // ========================
    // 设备状态
    // ========================

    private fun setupDeviceStatus() {
        val tvProfile = findViewById<TextView>(R.id.tvDeviceProfile)
        val tvBattery = findViewById<TextView>(R.id.tvBatteryWarning)
        val tvPerf = findViewById<TextView>(R.id.tvPerformanceParams)

        lifecycleScope.launch {
            serviceBus.deviceProfile.collectLatest { profile ->
                val label = when (profile) {
                    DeviceProfile.HIGH -> "HIGH"
                    DeviceProfile.MEDIUM -> "MEDIUM"
                    DeviceProfile.LOW -> "LOW"
                }
                tvProfile.text = "性能档位: $label"
            }
        }

        lifecycleScope.launch {
            serviceBus.performanceParams.collectLatest { params ->
                tvBattery.text = "电池警告: ${if (params.batteryWarning) "是 (<15%)" else "否"}"
                tvPerf.text = "性能参数: frameSkip=${params.frameSkip}, flashInterval=${params.flashInterval}ms"
            }
        }
    }

    // ========================
    // 事件日志 RecyclerView
    // ========================

    private fun setupEventLog() {
        val recycler = findViewById<RecyclerView>(R.id.recyclerEventLog)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = eventLogAdapter
    }

    private fun addLog(message: String) {
        val timestamp = dateFormat.format(Date())
        eventLogAdapter.addItem("[$timestamp] $message")
    }
}

// ========================
// RecyclerView Adapter
// ========================

class EventLogAdapter :
    RecyclerView.Adapter<EventLogAdapter.ViewHolder>() {

    private val items = mutableListOf<String>()

    fun addItem(item: String) {
        items.add(0, item)
        if (items.size > 200) {
            items.removeAt(items.lastIndex)
        }
        notifyItemInserted(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = items[position]
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }
}