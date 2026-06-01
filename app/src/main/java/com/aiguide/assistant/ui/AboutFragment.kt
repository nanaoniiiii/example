package com.aiguide.assistant.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aiguide.assistant.BuildConfig
import com.aiguide.assistant.R

class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvVersion).text =
            "版本 ${BuildConfig.VERSION_NAME}"

        view.findViewById<View>(R.id.btnGitHub).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.about_github_url))))
        }

        view.findViewById<View>(R.id.btnLicense).setOnClickListener {
            val text = getString(R.string.about_license_text)
            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.about_license_title)
                .setMessage(text)
                .setPositiveButton(android.R.string.ok, null)
                .create()
            dialog.show()
        }
    }
}
