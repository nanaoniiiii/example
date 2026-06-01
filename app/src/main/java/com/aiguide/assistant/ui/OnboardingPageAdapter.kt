package com.aiguide.assistant.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aiguide.assistant.R
import com.aiguide.assistant.databinding.ItemOnboardingPageBinding

class OnboardingPageAdapter(
    private val activity: OnboardingActivity
) : RecyclerView.Adapter<OnboardingPageAdapter.PageViewHolder>() {

    private val titles = intArrayOf(
        R.string.onboarding_page1_title,
        R.string.onboarding_page2_title,
        R.string.onboarding_page3_title,
        R.string.onboarding_page4_title
    )

    private val descriptions = intArrayOf(
        R.string.onboarding_page1_desc,
        R.string.onboarding_page2_desc,
        R.string.onboarding_page3_desc,
        R.string.onboarding_page4_desc
    )

    override fun getItemCount(): Int = titles.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(titles[position], descriptions[position])
    }

    inner class PageViewHolder(
        private val binding: ItemOnboardingPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(titleRes: Int, descRes: Int) {
            binding.tvTitle.text = activity.getString(titleRes)
            binding.tvDesc.text = activity.getString(descRes)
        }
    }
}
