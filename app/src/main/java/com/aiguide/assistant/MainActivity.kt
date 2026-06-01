package com.aiguide.assistant

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aiguide.assistant.databinding.ActivityMainBinding
import com.aiguide.assistant.ui.AboutFragment
import com.aiguide.assistant.ui.HomeFragment
import com.aiguide.assistant.ui.LabFragment
import com.aiguide.assistant.ui.OnboardingActivity
import com.aiguide.assistant.ui.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OnboardingActivity.isFirstLaunch(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNav()

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.navHome
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.navHome -> HomeFragment()
                R.id.navLab -> LabFragment()
                R.id.navSettings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    return@setOnItemSelectedListener false
                }
                R.id.navAbout -> AboutFragment()
                else -> return@setOnItemSelectedListener false
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()

            true
        }
    }
}
