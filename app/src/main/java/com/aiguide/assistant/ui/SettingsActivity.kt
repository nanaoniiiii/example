package com.aiguide.assistant.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiguide.assistant.R
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 设置页面：使用 RecyclerView 展示各项设置，绑定 ServiceBus StateFlow。
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var serviceBus: ServiceBus

    private val adapter = SettingsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        adapter.submitList(buildSettingsList())
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun buildSettingsList(): List<SettingItem> = listOf(
        SettingItem.Slider(
            key = "privacy_overlay_alpha",
            title = getString(R.string.setting_privacy_overlay_alpha),
            summary = getString(R.string.setting_privacy_overlay_alpha),
            valueFromFlow = { serviceBus.privacyOverlayAlpha.value },
            valueRange = 0..100,
            onValueChanged = { serviceBus.setPrivacyOverlayAlpha(it) }
        ),
        SettingItem.Switch(
            key = "voice_wake",
            title = getString(R.string.setting_voice_wake),
            summary = "语音唤醒后触发协助模式",
            valueFromFlow = { serviceBus.voiceWakeEnabled.value },
            onValueChanged = { serviceBus.voiceWakeEnabled.value = it }
        ),
        SettingItem.SliderFloat(
            key = "tts_rate",
            title = getString(R.string.setting_tts_rate),
            summary = "控制 TTS 朗读语速",
            valueFromFlow = { serviceBus.ttsRate.value },
            floatRange = 0.5f..2.0f,
            floatSteps = 30,
            displayFormat = { "%.1fx".format(it) },
            onValueChanged = { serviceBus.ttsRate.value = it }
        ),
        SettingItem.SliderFloat(
            key = "tts_pitch",
            title = getString(R.string.setting_tts_pitch),
            summary = "控制 TTS 朗读音调",
            valueFromFlow = { serviceBus.ttsPitch.value },
            floatRange = 0.5f..2.0f,
            floatSteps = 30,
            displayFormat = { "%.1f".format(it) },
            onValueChanged = { serviceBus.ttsPitch.value = it }
        ),
        SettingItem.SliderInt(
            key = "assist_timeout",
            title = getString(R.string.setting_assist_timeout),
            summary = "协助模式超时时间",
            valueFromFlow = { serviceBus.assistModeTimeout.value },
            valueRange = 1..5,
            displayFormat = { getString(R.string.setting_assist_timeout_format, it) },
            onValueChanged = { serviceBus.assistModeTimeout.value = it }
        ),
        SettingItem.Switch(
            key = "flashlight",
            title = getString(R.string.setting_flashlight),
            summary = "天黑时自动闪烁闪光灯提醒",
            valueFromFlow = { serviceBus.flashLightEnabled.value },
            onValueChanged = { serviceBus.flashLightEnabled.value = it }
        ),
        SettingItem.Switch(
            key = "low_power",
            title = getString(R.string.setting_low_power),
            summary = "降低帧率和处理频率以节省电量",
            valueFromFlow = { serviceBus.lowPowerMode.value },
            onValueChanged = { serviceBus.lowPowerMode.value = it }
        )
    )
}
