package com.aiguide.assistant

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aiguide.assistant.databinding.ActivityMainBinding
import com.aiguide.assistant.engine.DeviceProfile as DeviceProfileEngine
import com.aiguide.assistant.service.DeviceProfile
import com.aiguide.assistant.service.ServiceBus
import com.aiguide.assistant.ui.OnboardingActivity
import com.aiguide.assistant.ui.SettingsActivity
import com.aiguide.assistant.ui.StatusIndicator
import com.aiguide.assistant.ui.TestPanelActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AIGuide 主 Activity — 底部导航：首页 / 设置 / 状态面板
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /*
    @Inject
    lateinit var serviceBus: ServiceBus

    @Inject
    lateinit var statusIndicator: StatusIndicator

    @Inject
    lateinit var deviceProfileEngine: DeviceProfileEngine
    */

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate: start")

        // 检查首次启动
        if (OnboardingActivity.isFirstLaunch(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        android.util.Log.d("MainActivity", "onCreate: success - minimal version")

        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navHome -> true
                R.id.navSettings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.navTest -> {
                    startActivity(Intent(this, TestPanelActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun observeStateFlows() {
        // 蒙版状态
        lifecycleScope.launch {
            serviceBus.privacyOverlayAlpha.collectLatest { alpha ->
                val on = alpha > 50
                binding.tvOverlayStatus.text = if (on) getString(R.string.status_on) else getString(R.string.status_off)
                binding.tvOverlayStatus.setTextColor(
                    getColor(if (on) R.color.status_on else R.color.status_off)
                )
                binding.switchOverlay.isChecked = on
            }
        }

        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            serviceBus.setPrivacyOverlayAlpha(if (isChecked) 100 else 0)
        }

        // 摄像头状态
        lifecycleScope.launch {
            serviceBus.cameraEnabled.collectLatest { enabled ->
                binding.tvCameraStatus.text = if (enabled) getString(R.string.status_on) else getString(R.string.status_off)
                binding.tvCameraStatus.setTextColor(
                    getColor(if (enabled) R.color.status_on else R.color.status_off)
                )
                binding.switchCamera.isChecked = enabled
            }
        }

        binding.switchCamera.setOnCheckedChangeListener { _, isChecked ->
            serviceBus.cameraEnabled.value = isChecked
        }

        // 电池状态
        lifecycleScope.launch {
            val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1

            val pct = if (scale > 0) level * 100 / scale else -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || status == android.os.BatteryManager.BATTERY_STATUS_FULL

            val batteryText = when {
                pct < 0 -> getString(R.string.status_unknown)
                pct <= 15 -> "${getString(R.string.status_battery_low)} ${pct}%"
                isCharging -> "${getString(R.string.status_charging)} ${pct}%"
                else -> "${getString(R.string.status_battery_normal)} ${pct}%"
            }
            binding.tvBatteryStatus.text = batteryText
        }

        // 设备档位
        lifecycleScope.launch {
            serviceBus.deviceProfile.collectLatest { profile ->
                val tierText = when (profile) {
                    DeviceProfile.HIGH -> "HIGH (8+核, 6GB+ RAM)"
                    DeviceProfile.MEDIUM -> "MEDIUM (4-7核, 3-5GB RAM)"
                    DeviceProfile.LOW -> "LOW (基础模式)"
                }
                binding.tvDeviceTier.text = tierText
            }
        }
    }
    */
}
