package com.aiguide.assistant.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aiguide.assistant.BuildConfig
import com.aiguide.assistant.R
import com.aiguide.assistant.service.AssistMode
import com.aiguide.assistant.service.ServiceBus

class SettingsAdapter(private val serviceBus: ServiceBus) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_CATEGORY = 0
        const val TYPE_SWITCH = 1
        const val TYPE_SEEKBAR = 2
        const val TYPE_RADIO = 3
        const val TYPE_CLICK = 4
    }

    private var onAboutClick: (() -> Unit)? = null
    private var onGitHubClick: (() -> Unit)? = null
    private var onLicenseClick: (() -> Unit)? = null

    fun setOnAboutClick(l: () -> Unit) { onAboutClick = l }
    fun setOnGitHubClick(l: () -> Unit) { onGitHubClick = l }
    fun setOnLicenseClick(l: () -> Unit) { onLicenseClick = l }

    private val items: List<SettingItem> = listOf(
        // AI配置
        SettingItem.Category("AI配置"),
        SettingItem.Radio("视觉AI模型", listOf("本地TFLite", "云端混元"), 0) { idx ->
            serviceBus.setVisionModel(if (idx == 0) "tflite" else "hunyuan")
        },
        SettingItem.Radio("语音识别引擎", listOf("Vosk离线", "云端API"), 0) { idx ->
            serviceBus.setAsrEngine(if (idx == 0) "vosk" else "cloud")
        },
        SettingItem.Radio("TTS引擎", listOf("系统", "自定义"), 0) { idx ->
            serviceBus.setTtsEngine(if (idx == 0) "system" else "custom")
        },
        SettingItem.Seekbar("唤醒词灵敏度", 0, 100, serviceBus.wakeWordSensitivity.value) { v ->
            serviceBus.setWakeWordSensitivity(v)
        },
        SettingItem.Radio("协助模式超时", listOf("1分钟", "3分钟", "5分钟"), 1) { idx ->
            val timeoutMinutes = when (idx) { 0 -> 1; 1 -> 3; 2 -> 5; else -> 3 }
            serviceBus.setAssistTimeout(timeoutMinutes * 60)
        },

        // 显示与遮罩
        SettingItem.Category("显示与遮罩"),
        SettingItem.Switch("隐私蒙版", serviceBus.privacyOverlayAlpha.value > 0) { enabled ->
            serviceBus.setPrivacyOverlayAlpha(if (enabled) 100 else 0)
        },
        SettingItem.Seekbar("蒙版透明度", 0, 100, serviceBus.privacyOverlayAlpha.value) { v ->
            serviceBus.setPrivacyOverlayAlpha(v)
        },
        SettingItem.Switch("悬浮窗状态指示器", true) { _ -> },
        SettingItem.Radio("主题模式", listOf("跟随系统", "浅色", "深色"), 0) { _ -> },

        // 导航安全
        SettingItem.Category("导航安全"),
        SettingItem.Seekbar("危险播报音量", 0, 100, 75) { v ->
            serviceBus.setHazardVolume(v)
        },
        SettingItem.Radio("危险等级过滤", listOf("仅严重", "全部"), 0) { idx ->
            serviceBus.setHazardFilterMode(if (idx == 0) "critical" else "all")
        },
        SettingItem.Switch("天黑自动闪光灯", serviceBus.flashLightEnabled.value) { enabled ->
            serviceBus.flashLightEnabled.value = enabled
        },

        // 关于
        SettingItem.Category("关于"),
        SettingItem.Click("应用版本") { onAboutClick?.invoke() },
        SettingItem.Click("应用说明") { onAboutClick?.invoke() },
        SettingItem.Click("开源许可") { onLicenseClick?.invoke() },
        SettingItem.Click("GitHub") { onGitHubClick?.invoke() }
    )

    override fun getItemViewType(position: Int): Int = items[position].type

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CATEGORY -> {
                val v = inflater.inflate(R.layout.item_setting_category, parent, false)
                CategoryVH(v)
            }
            TYPE_SWITCH -> {
                val v = inflater.inflate(R.layout.item_setting_universal, parent, false)
                SwitchVH(v)
            }
            TYPE_SEEKBAR -> {
                val v = inflater.inflate(R.layout.item_setting_seekbar, parent, false)
                SeekbarVH(v)
            }
            TYPE_RADIO -> {
                val v = inflater.inflate(R.layout.item_setting_radio, parent, false)
                RadioVH(v)
            }
            TYPE_CLICK -> {
                val v = inflater.inflate(R.layout.item_setting_universal, parent, false)
                ClickVH(v)
            }
            else -> throw IllegalArgumentException("Unknown type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is CategoryVH -> holder.bind(item as SettingItem.Category)
            is SwitchVH -> holder.bind(item as SettingItem.Switch)
            is SeekbarVH -> holder.bind(item as SettingItem.Seekbar)
            is RadioVH -> holder.bind(item as SettingItem.Radio)
            is ClickVH -> holder.bind(item as SettingItem.Click)
        }
    }

    // ============ ViewHolder ============

    class CategoryVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvCategory)
        fun bind(item: SettingItem.Category) { tv.text = item.title }
    }

    class SwitchVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLabel: TextView = view.findViewById(R.id.tvSettingLabel)
        private val switch: Switch = view.findViewById(R.id.swSetting)
        private var item: SettingItem.Switch? = null

        init {
            switch.setOnCheckedChangeListener { _, isChecked ->
                item?.onChange?.invoke(isChecked)
            }
        }

        fun bind(item: SettingItem.Switch) {
            this.item = item
            tvLabel.text = item.title
            switch.isChecked = item.initialValue
        }
    }

    class SeekbarVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLabel: TextView = view.findViewById(R.id.tvSettingLabel)
        private val seekBar: SeekBar = view.findViewById(R.id.seekSetting)
        private val tvValue: TextView = view.findViewById(R.id.tvSettingValue)
        private var item: SettingItem.Seekbar? = null

        init {
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    tvValue.text = "${progress}%"
                    if (fromUser) item?.onChange?.invoke(progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        fun bind(item: SettingItem.Seekbar) {
            this.item = item
            tvLabel.text = item.title
            seekBar.max = item.max
            seekBar.progress = item.initialValue
            tvValue.text = "${item.initialValue}%"
        }
    }

    class RadioVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLabel: TextView = view.findViewById(R.id.tvSettingLabel)
        private val radioGroup: RadioGroup = view.findViewById(R.id.rgSetting)
        private var item: SettingItem.Radio? = null

        init {
            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                val radio = view.findViewById<RadioButton>(checkedId)
                val idx = radioGroup.indexOfChild(radio)
                item?.onChange?.invoke(idx)
            }
        }

        fun bind(item: SettingItem.Radio) {
            this.item = item
            tvLabel.text = item.title
            radioGroup.removeAllViews()
            for ((i, opt) in item.options.withIndex()) {
                val rb = RadioButton(radioGroup.context).apply {
                    text = opt
                    id = View.generateViewId()
                    layoutParams = RadioGroup.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                radioGroup.addView(rb)
                if (i == item.initialIndex) radioGroup.check(rb.id)
            }
        }
    }

    class ClickVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLabel: TextView = view.findViewById(R.id.tvSettingLabel)

        fun bind(item: SettingItem.Click) {
            tvLabel.text = item.title
            itemView.setOnClickListener { item.onClick() }
        }
    }

    // ============ Setting Item sealed class ============

    sealed class SettingItem(val type: Int) {
        data class Category(val title: String) : SettingItem(TYPE_CATEGORY)
        data class Switch(val title: String, val initialValue: Boolean, val onChange: (Boolean) -> Unit) : SettingItem(TYPE_SWITCH)
        data class Seekbar(val title: String, val min: Int, val max: Int, val initialValue: Int, val onChange: (Int) -> Unit) : SettingItem(TYPE_SEEKBAR)
        data class Radio(val title: String, val options: List<String>, val initialIndex: Int, val onChange: (Int) -> Unit) : SettingItem(TYPE_RADIO)
        data class Click(val title: String, val onClick: () -> Unit) : SettingItem(TYPE_CLICK)
    }
}
