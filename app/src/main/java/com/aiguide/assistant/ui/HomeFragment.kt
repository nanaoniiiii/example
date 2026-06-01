package com.aiguide.assistant.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aiguide.assistant.R
import com.aiguide.assistant.service.AssistMode
import com.aiguide.assistant.service.DeviceProfile
import com.aiguide.assistant.service.ServiceBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    @Inject
    lateinit var serviceBus: ServiceBus

    private val cards = listOf(
        DashboardCard(R.drawable.ic_privacy, R.string.dashboard_privacy, R.string.dashboard_privacy_desc),
        DashboardCard(R.drawable.ic_voice, R.string.dashboard_voice, R.string.dashboard_voice_desc),
        DashboardCard(R.drawable.ic_vision, R.string.dashboard_vision, R.string.dashboard_vision_desc),
        DashboardCard(R.drawable.ic_navigation, R.string.dashboard_nav, R.string.dashboard_nav_desc),
        DashboardCard(R.drawable.ic_auto, R.string.dashboard_auto, R.string.dashboard_auto_desc),
        DashboardCard(R.drawable.ic_flashlight, R.string.dashboard_flash, R.string.dashboard_flash_desc),
        DashboardCard(R.drawable.ic_assist, R.string.dashboard_assist, R.string.dashboard_assist_desc),
        DashboardCard(R.drawable.ic_device, R.string.dashboard_device, R.string.dashboard_device_desc)
    )

    private var adapter: DashboardAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val gv = view.findViewById<GridView>(R.id.gvDashboard)
        adapter = DashboardAdapter(layoutInflater)
        gv.adapter = adapter
        gv.setOnItemClickListener { _, _, position, _ -> onCardClick(position) }
        observeStates()
    }

    private fun onCardClick(position: Int) {
        when (position) {
            0 -> serviceBus.setPrivacyOverlayAlpha(
                if (serviceBus.privacyOverlayAlpha.value > 0) 0 else 100
            )
            1 -> parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, LabFragment())
                .addToBackStack(null).commit()
            2 -> parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, LabFragment())
                .addToBackStack(null).commit()
            3 -> {} // 导航安全 - 暂不跳转
            4 -> {} // 自动操作 - 暂不跳转
            5 -> serviceBus.flashLightEnabled.value = !serviceBus.flashLightEnabled.value
            6 -> {
                val next = when (serviceBus.assistMode.value) {
                    AssistMode.IDLE -> AssistMode.PASSIVE
                    AssistMode.PASSIVE -> AssistMode.ACTIVE
                    AssistMode.ACTIVE -> AssistMode.IDLE
                }
                serviceBus.setAssistMode(next)
            }
            7 -> {} // 设备状态 - 暂不跳转
        }
    }

    private fun observeStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            serviceBus.privacyOverlayAlpha.collectLatest { adapter?.notifyDataSetChanged() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            serviceBus.cameraEnabled.collectLatest { adapter?.notifyDataSetChanged() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            serviceBus.isDarkEnvironment.collectLatest { adapter?.notifyDataSetChanged() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            serviceBus.flashLightEnabled.collectLatest { adapter?.notifyDataSetChanged() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            serviceBus.assistMode.collectLatest { adapter?.notifyDataSetChanged() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            serviceBus.deviceProfile.collectLatest { adapter?.notifyDataSetChanged() }
        }
    }

    data class DashboardCard(
        val iconRes: Int,
        val titleRes: Int,
        val descRes: Int
    )

    inner class DashboardAdapter(private val inflater: LayoutInflater) : BaseAdapter() {
        override fun getCount(): Int = cards.size
        override fun getItem(position: Int): DashboardCard = cards[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.item_dashboard_card, parent, false)
            val card = cards[position]
            view.findViewById<ImageView>(R.id.ivIcon).setImageResource(card.iconRes)
            view.findViewById<TextView>(R.id.tvTitle).setText(card.titleRes)
            view.findViewById<TextView>(R.id.tvDescription).setText(card.descRes)
            val statusDot = view.findViewById<ImageView>(R.id.ivStatus)
            statusDot.setImageResource(getStatusDot(position))
            return view
        }

        private fun getStatusDot(position: Int): Int {
            return when (position) {
                0 -> if (serviceBus.privacyOverlayAlpha.value > 0) R.drawable.dot_green else R.drawable.dot_red
                1 -> R.drawable.dot_green // voice always active
                2 -> if (serviceBus.cameraEnabled.value) R.drawable.dot_green else R.drawable.dot_red
                3 -> R.drawable.dot_green
                4 -> R.drawable.dot_green
                5 -> if (serviceBus.flashLightEnabled.value) R.drawable.dot_green else R.drawable.dot_red
                6 -> when (serviceBus.assistMode.value) {
                    AssistMode.ACTIVE -> R.drawable.dot_green
                    AssistMode.PASSIVE -> R.drawable.dot_yellow
                    AssistMode.IDLE -> R.drawable.dot_red
                }
                7 -> when (serviceBus.deviceProfile.value) {
                    DeviceProfile.HIGH -> R.drawable.dot_green
                    DeviceProfile.MEDIUM -> R.drawable.dot_yellow
                    DeviceProfile.LOW -> R.drawable.dot_red
                }
                else -> R.drawable.dot_green
            }
        }
    }
}
