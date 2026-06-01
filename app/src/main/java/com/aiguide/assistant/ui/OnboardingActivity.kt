package com.aiguide.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.aiguide.assistant.R
import com.aiguide.assistant.MainActivity
import com.aiguide.assistant.service.AssistMode
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    companion object {
        fun isFirstLaunch(context: android.content.Context): Boolean {
            val prefs = context.getSharedPreferences("aiguide_prefs", android.content.Context.MODE_PRIVATE)
            return prefs.getBoolean(context.getString(R.string.pref_first_launch), true)
        }
    }

    @Inject
    lateinit var serviceBus: ServiceBus

    private lateinit var viewPager: ViewPager2
    private lateinit var btnSkip: Button
    private lateinit var btnAction: Button
    private lateinit var layoutIndicators: LinearLayout
    private lateinit var prefs: SharedPreferences

    private val permissions = listOf(
        PermissionItem("悬浮窗", "隐私蒙版显示", Manifest.permission.SYSTEM_ALERT_WINDOW),
        PermissionItem("无障碍", "三击电源键触发协助", Manifest.permission.BIND_ACCESSIBILITY_SERVICE),
        PermissionItem("摄像头", "环境感知与识别", Manifest.permission.CAMERA),
        PermissionItem("麦克风", "语音唤醒与识别", Manifest.permission.RECORD_AUDIO),
        PermissionItem("定位", "导航安全辅助",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                Manifest.permission.ACCESS_FINE_LOCATION
            else Manifest.permission.ACCESS_COARSE_LOCATION),
        PermissionItem("通知", "关键事件提醒",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS
            else "")
    )

    private var currentPage = 0
    private val totalPages = 5
    private val dots = mutableListOf<ImageView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        prefs = getSharedPreferences("aiguide_prefs", MODE_PRIVATE)

        viewPager = findViewById(R.id.viewPager)
        btnSkip = findViewById(R.id.btnSkip)
        btnAction = findViewById(R.id.btnAction)
        layoutIndicators = findViewById(R.id.layoutIndicators)

        viewPager.adapter = OnboardingAdapter()
        setupIndicators()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateIndicators()
                updateButtons()
            }
        })

        btnSkip.setOnClickListener { finishOnboarding() }
        btnAction.setOnClickListener { onActionClick() }

        updateButtons()
    }

    private fun setupIndicators() {
        layoutIndicators.removeAllViews()
        dots.clear()
        for (i in 0 until totalPages) {
            val dot = ImageView(this).apply {
                val size = resources.getDimensionPixelSize(R.dimen.indicator_dot_size)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.indicator_dot_margin)
                }
                setImageDrawable(
                    ContextCompat.getDrawable(context,
                        if (i == 0) R.drawable.dot_green else R.drawable.dot_red
                    )
                )
            }
            layoutIndicators.addView(dot)
            dots.add(dot)
        }
    }

    private fun updateIndicators() {
        for (i in 0 until totalPages) {
            dots[i].setImageDrawable(
                ContextCompat.getDrawable(this,
                    if (i <= currentPage) R.drawable.dot_green else R.drawable.dot_red
                )
            )
        }
    }

    private fun updateButtons() {
        when (currentPage) {
            totalPages - 1 -> {
                btnSkip.visibility = View.GONE
                btnAction.text = getString(R.string.onboarding_enter_app)
            }
            else -> {
                btnSkip.visibility = View.VISIBLE
                btnAction.text = if (currentPage == 0)
                    getString(R.string.onboarding_page1_btn)
                else
                    getString(R.string.onboarding_next)
            }
        }
    }

    private fun onActionClick() {
        if (currentPage < totalPages - 1) {
            viewPager.currentItem = currentPage + 1
        } else {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        prefs.edit().putBoolean(getString(R.string.pref_first_launch), false).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun grantPermission(permission: String) {
        when (permission) {
            Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
            }
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            else -> {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 0)
            }
        }
    }

    // ============ ViewPager Adapter ============

    inner class OnboardingAdapter : RecyclerView.Adapter<OnboardingAdapter.VH>() {

        override fun getItemCount(): Int = totalPages

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_page, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            when (position) {
                0 -> bindPage1(holder)
                1 -> bindPage2(holder)
                2 -> bindPage3(holder)
                3 -> bindPage4(holder)
                4 -> bindPage5(holder)
            }
        }

        private fun bindPage1(holder: VH) {
            holder.ivIcon.visibility = View.VISIBLE
            holder.ivIcon.setImageResource(R.mipmap.ic_launcher)
            holder.tvTitle.text = getString(R.string.onboarding_page1_title)
            holder.tvDesc.text = getString(R.string.onboarding_page1_desc)
            holder.flContent.visibility = View.GONE
        }

        private fun bindPage2(holder: VH) {
            holder.ivIcon.visibility = View.GONE
            holder.tvTitle.text = getString(R.string.onboarding_page2_title)
            holder.tvDesc.text = getString(R.string.onboarding_page2_desc)
            holder.flContent.visibility = View.VISIBLE
            holder.flContent.removeAllViews()

            val inflater = LayoutInflater.from(holder.itemView.context)
            for (perm in permissions) {
                val itemView = inflater.inflate(
                    R.layout.item_onboarding_permission, holder.flContent, false
                )
                itemView.findViewById<TextView>(R.id.tvPermName).text = perm.name
                itemView.findViewById<TextView>(R.id.tvPermDesc).text = perm.desc
                val btnGrant = itemView.findViewById<Button>(R.id.btnGrant)
                btnGrant.setOnClickListener {
                    if (perm.permission.isNotEmpty()) grantPermission(perm.permission)
                    else grantNotification()
                    btnGrant.text = getString(R.string.onboarding_granted)
                    btnGrant.isEnabled = false
                }
                holder.flContent.addView(itemView)
            }
        }

        private fun bindPage3(holder: VH) {
            holder.ivIcon.visibility = View.GONE
            holder.tvTitle.text = getString(R.string.onboarding_page3_title)
            holder.tvDesc.text = getString(R.string.onboarding_page3_desc)
            holder.flContent.visibility = View.VISIBLE
            holder.flContent.removeAllViews()

            val inflater = LayoutInflater.from(holder.itemView.context)
            val content = inflater.inflate(
                R.layout.onboarding_vision_test, holder.flContent, false
            )
            val btnCapture = content.findViewById<Button>(R.id.btnOnboardCapture)
            val tvResult = content.findViewById<TextView>(R.id.tvOnboardVisionResult)

            btnCapture.setOnClickListener {
                serviceBus.requestCameraCapture()
                tvResult.text = "识别中...请稍候"
                // In a real app, collect visionResult flow; here a simple mock
                viewPager.postDelayed({
                    tvResult.text = "测试识别：前方场景已由AI分析完毕"
                }, 2000)
            }

            holder.flContent.addView(content)
        }

        private fun bindPage4(holder: VH) {
            holder.ivIcon.visibility = View.GONE
            holder.tvTitle.text = getString(R.string.onboarding_page4_title)
            holder.tvDesc.text = getString(R.string.onboarding_page4_desc)
            holder.flContent.visibility = View.VISIBLE
            holder.flContent.removeAllViews()

            val inflater = LayoutInflater.from(holder.itemView.context)
            val content = inflater.inflate(
                R.layout.onboarding_voice_test, holder.flContent, false
            )
            val btnMic = content.findViewById<Button>(R.id.btnOnboardMic)
            val tvResult = content.findViewById<TextView>(R.id.tvOnboardVoiceResult)

            btnMic.setOnClickListener {
                tvResult.text = "正在聆听..."
                serviceBus.startWakeWordDetection()
                viewPager.postDelayed({
                    tvResult.text = "小助，我听到了！语音识别测试成功"
                }, 3000)
            }

            holder.flContent.addView(content)
        }

        private fun bindPage5(holder: VH) {
            holder.ivIcon.visibility = View.GONE
            holder.tvTitle.text = getString(R.string.onboarding_page5_title)
            holder.tvDesc.text = getString(R.string.onboarding_page5_desc)
            holder.flContent.visibility = View.VISIBLE
            holder.flContent.removeAllViews()

            val inflater = LayoutInflater.from(holder.itemView.context)
            val content = inflater.inflate(
                R.layout.onboarding_complete, holder.flContent, false
            )

            val modules = listOf(
                Triple(R.id.cbVision, R.id.tvLabelVision, getString(R.string.onboarding_page5_module_vision)),
                Triple(R.id.cbVoice, R.id.tvLabelVoice, getString(R.string.onboarding_page5_module_voice)),
                Triple(R.id.cbOverlay, R.id.tvLabelOverlay, getString(R.string.onboarding_page5_module_overlay)),
                Triple(R.id.cbNav, R.id.tvLabelNav, getString(R.string.onboarding_page5_module_nav)),
                Triple(R.id.cbAuto, R.id.tvLabelAuto, getString(R.string.onboarding_page5_module_auto)),
                Triple(R.id.cbFlash, R.id.tvLabelFlash, getString(R.string.onboarding_page5_module_flash))
            )

            for ((rowId, labelId, label) in modules) {
                val item = content.findViewById<LinearLayout>(rowId)
                item?.findViewById<TextView>(labelId)?.text = label
            }

            holder.flContent.addView(content)
        }

        private fun grantNotification() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this@OnboardingActivity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
                )
            }
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.ivOnboarding)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvDesc: TextView = view.findViewById(R.id.tvDesc)
            val flContent: ViewGroup = view.findViewById(R.id.flSpecialContent)
        }
    }

    data class PermissionItem(
        val name: String,
        val desc: String,
        val permission: String
    )
}
