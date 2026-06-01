package com.aiguide.assistant.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiguide.assistant.R
import com.aiguide.assistant.service.ServiceBus
import com.aiguide.assistant.service.TtsPriority
import com.aiguide.assistant.service.VisionResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LabFragment : Fragment(R.layout.fragment_lab) {

    @Inject
    lateinit var serviceBus: ServiceBus

    private val visionHistory = mutableListOf<VisionHistoryItem>()
    private var visionHistoryAdapter: VisionHistoryAdapter? = null
    private var isRecording = false
    private var lastVoiceResult = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupVisionModule(view)
        setupVoiceModule(view)
    }

    // ============ 视觉AI测试 ============

    private fun setupVisionModule(view: View) {
        val tvResult = view.findViewById<TextView>(R.id.tvVisionResult)
        val layoutMeta = view.findViewById<View>(R.id.layoutVisionMeta)
        val tvConfidence = view.findViewById<TextView>(R.id.tvConfidence)
        val tvTime = view.findViewById<TextView>(R.id.tvVisionTime)
        val rvHistory = view.findViewById<RecyclerView>(R.id.rvVisionHistory)
        val tvEmpty = view.findViewById<TextView>(R.id.tvVisionEmpty)

        visionHistoryAdapter = VisionHistoryAdapter()
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = visionHistoryAdapter
        rvHistory.visibility = View.GONE
        tvEmpty.visibility = View.VISIBLE

        view.findViewById<Button>(R.id.btnCapture).setOnClickListener {
            val startTime = System.currentTimeMillis()
            tvResult.text = "识别中..."
            tvResult.visibility = View.VISIBLE

            serviceBus.requestCameraCapture()

            // 收集第一个有效视觉结果（过滤内部信号）
            viewLifecycleOwner.lifecycleScope.launch {
                val result = serviceBus.visionResult
                    .filter { it.label != "__CAPTURE_REQUEST__" }
                    .first()

                val elapsed = System.currentTimeMillis() - startTime
                val text = result.description.ifBlank { result.label }
                val confidence = (result.confidence * 100).toInt()

                val item = VisionHistoryItem(
                    text = text,
                    confidence = result.confidence * 100,
                    timeMs = elapsed
                )
                visionHistory.add(0, item)
                if (visionHistory.size > 5) visionHistory.removeAt(visionHistory.size - 1)
                visionHistoryAdapter?.notifyDataSetChanged()

                tvResult.text = text
                tvResult.visibility = View.VISIBLE
                layoutMeta.visibility = View.VISIBLE
                tvConfidence.text = "置信度: ${confidence}%"
                tvTime.text = "耗时: ${elapsed}ms"

                rvHistory.visibility = View.VISIBLE
                tvEmpty.visibility = View.GONE

                serviceBus.requestTts(text, TtsPriority.NORMAL)
            }
        }
    }

    // ============ 语音AI测试 ============

    private fun setupVoiceModule(view: View) {
        val frameMic = view.findViewById<View>(R.id.frameMic)
        val ivMic = view.findViewById<ImageView>(R.id.ivMicIcon)
        val viewWave = view.findViewById<WaveformView>(R.id.viewWaveform)
        val tvResult = view.findViewById<TextView>(R.id.tvVoiceResult)
        val btnPlay = view.findViewById<Button>(R.id.btnPlayTts)

        frameMic.setOnClickListener {
            if (isRecording) {
                isRecording = false
                ivMic.setImageResource(R.drawable.ic_mic)
                viewWave.stopAnimation()
                viewWave.visibility = View.INVISIBLE
                serviceBus.endListening()
            } else {
                isRecording = true
                ivMic.setImageResource(R.drawable.ic_mic_filled)
                viewWave.visibility = View.VISIBLE
                viewWave.startAnimation()
                tvResult.text = ""
                serviceBus.startWakeWordDetection()
            }
        }

        // 监听语音命令（过滤内部信号）
        viewLifecycleOwner.lifecycleScope.launch {
            serviceBus.voiceCommand.collect { text ->
                if (text.startsWith("__")) return@collect
                if (isRecording) {
                    lastVoiceResult = text
                    tvResult.text = text
                    btnPlay.isEnabled = true
                }
            }
        }

        btnPlay.setOnClickListener {
            if (lastVoiceResult.isNotBlank()) {
                serviceBus.requestTts(lastVoiceResult, TtsPriority.NORMAL)
            }
        }
    }

    // ============ 数据类与适配器 ============

    data class VisionHistoryItem(
        val text: String,
        val confidence: Float,
        val timeMs: Long
    )

    inner class VisionHistoryAdapter :
        RecyclerView.Adapter<VisionHistoryAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_vision_history, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = visionHistory[position]
            holder.tvIndex.text = "${position + 1}"
            holder.tvResult.text = item.text
            holder.tvConfidence.text = "置信度: ${item.confidence.toInt()}%"
            holder.tvTime.text = "${item.timeMs}ms"
        }

        override fun getItemCount(): Int = visionHistory.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvIndex: TextView = view.findViewById(R.id.tvIndex)
            val tvResult: TextView = view.findViewById(R.id.tvResult)
            val tvConfidence: TextView = view.findViewById(R.id.tvConfidence)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
        }
    }
}
