package com.aiguide.assistant.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.aiguide.assistant.MainActivity
import com.aiguide.assistant.R
import com.google.android.material.button.MaterialButton

/**
 * 首次启动引导页 Activity，使用 ViewPager2 + 指示器实现分页引导。
 */
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val PREF_NAME = "aiguide_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch_completed"
        private const val TOTAL_PAGES = 4

        fun isFirstLaunch(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return !prefs.getBoolean(KEY_FIRST_LAUNCH, false)
        }

        fun markCompleted(context: Context) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FIRST_LAUNCH, true)
                .apply()
        }
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var btnAction: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var indicatorLayout: LinearLayout

    private val pageColors = intArrayOf(
        android.R.color.holo_blue_dark,
        android.R.color.holo_green_dark,
        android.R.color.holo_orange_dark,
        android.R.color.holo_purple
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        btnAction = findViewById(R.id.btnAction)
        btnSkip = findViewById(R.id.btnSkip)
        indicatorLayout = findViewById(R.id.indicatorLayout)

        viewPager.adapter = OnboardingPageAdapter(this)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUI(position)
            }
        })

        btnSkip.setOnClickListener { navigateToMain() }
        btnAction.setOnClickListener {
            val current = viewPager.currentItem
            if (current < TOTAL_PAGES - 1) {
                viewPager.currentItem = current + 1
            } else {
                navigateToMain()
            }
        }

        // 拦截返回键：回到上一页
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewPager.currentItem > 0) {
                    viewPager.currentItem = viewPager.currentItem - 1
                } else {
                    finish()
                }
            }
        })

        createIndicators()
        updateUI(0)
    }

    private fun createIndicators() {
        indicatorLayout.removeAllViews()
        for (i in 0 until TOTAL_PAGES) {
            val dot = View(this).apply {
                val size = resources.getDimensionPixelSize(R.dimen.indicator_dot_size)
                val margin = resources.getDimensionPixelSize(R.dimen.indicator_dot_margin)
                val params = LinearLayout.LayoutParams(size, size)
                params.setMargins(margin, 0, margin, 0)
                layoutParams = params
                background = ContextCompat.getDrawable(context, R.drawable.indicator_dot_selector)
            }
            indicatorLayout.addView(dot)
        }
    }

    private fun updateUI(position: Int) {
        // 更新指示器
        for (i in 0 until indicatorLayout.childCount) {
            val dot = indicatorLayout.getChildAt(i)
            dot.isSelected = i == position
        }

        // 更新背景色
        viewPager.setBackgroundColor(
            ContextCompat.getColor(this, pageColors[position.coerceIn(pageColors.indices)])
        )

        // 更新按钮
        if (position == TOTAL_PAGES - 1) {
            btnAction.text = getString(R.string.onboarding_start)
            btnSkip.visibility = View.GONE
        } else {
            btnAction.text = getString(R.string.onboarding_next)
            btnSkip.visibility = View.VISIBLE
        }
    }

    private fun navigateToMain() {
        markCompleted(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
