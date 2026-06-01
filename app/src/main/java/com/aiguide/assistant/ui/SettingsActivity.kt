package com.aiguide.assistant.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aiguide.assistant.BuildConfig
import com.aiguide.assistant.R
import com.aiguide.assistant.service.AssistMode
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var serviceBus: ServiceBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvSettings)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = SettingsAdapter(serviceBus).apply {
            setOnAboutClick { showAbout() }
            setOnGitHubClick { openGitHub() }
            setOnLicenseClick { showLicenses() }
        }
    }

    private fun showAbout() {
        val text = getString(R.string.about_description_text, BuildConfig.VERSION_NAME)
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_category_about)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun openGitHub() {
        startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse(getString(R.string.about_github_url))))
    }

    private fun showLicenses() {
        val text = getString(R.string.about_license_text)
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.about_license_title)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
