package com.aiguide.assistant.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aiguide.assistant.R
import com.aiguide.assistant.databinding.ItemSettingSliderBinding
import com.aiguide.assistant.databinding.ItemSettingSwitchBinding
import kotlin.math.roundToInt

/**
 * 设置项数据类。
 */
sealed class SettingItem(open val key: String, val viewType: Int) {

    companion object {
        const val TYPE_SWITCH = 0
        const val TYPE_SLIDER = 1
        const val TYPE_SLIDER_INT = 2
        const val TYPE_SLIDER_FLOAT = 3
    }

    data class Switch(
        override val key: String,
        val title: String,
        val summary: String,
        val valueFromFlow: () -> Boolean,
        val onValueChanged: (Boolean) -> Unit
    ) : SettingItem(key, TYPE_SWITCH)

    data class Slider(
        override val key: String,
        val title: String,
        val summary: String,
        val valueFromFlow: () -> Int,
        val valueRange: IntRange,
        val onValueChanged: (Int) -> Unit
    ) : SettingItem(key, TYPE_SLIDER)

    data class SliderInt(
        override val key: String,
        val title: String,
        val summary: String,
        val valueFromFlow: () -> Int,
        val valueRange: IntRange,
        val displayFormat: (Int) -> String = { it.toString() },
        val onValueChanged: (Int) -> Unit
    ) : SettingItem(key, TYPE_SLIDER_INT)

    data class SliderFloat(
        override val key: String,
        val title: String,
        val summary: String,
        val valueFromFlow: () -> Float,
        val floatRange: ClosedFloatingPointRange<Float>,
        val floatSteps: Int = 30,
        val displayFormat: (Float) -> String = { "%.1f".format(it) },
        val onValueChanged: (Float) -> Unit
    ) : SettingItem(key, TYPE_SLIDER_FLOAT)
}

/**
 * 设置列表适配器。
 */
class SettingsAdapter :
    ListAdapter<SettingItem, RecyclerView.ViewHolder>(SettingItemDiffCallback()) {

    override fun getItemViewType(position: Int): Int = getItem(position).viewType

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            SettingItem.TYPE_SWITCH -> {
                val binding = ItemSettingSwitchBinding.inflate(inflater, parent, false)
                SwitchViewHolder(binding)
            }
            else -> {
                val binding = ItemSettingSliderBinding.inflate(inflater, parent, false)
                SliderViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SettingItem.Switch -> (holder as SwitchViewHolder).bind(item)
            is SettingItem.Slider -> (holder as SliderViewHolder).bindSlider(item)
            is SettingItem.SliderInt -> (holder as SliderViewHolder).bindSliderInt(item)
            is SettingItem.SliderFloat -> (holder as SliderViewHolder).bindSliderFloat(item)
        }
    }

    class SwitchViewHolder(private val binding: ItemSettingSwitchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SettingItem.Switch) {
            binding.tvTitle.text = item.title
            binding.tvSummary.text = item.summary
            binding.switchItem.isChecked = item.valueFromFlow()
            binding.switchItem.setOnCheckedChangeListener { _, isChecked ->
                item.onValueChanged(isChecked)
            }
        }
    }

    class SliderViewHolder(private val binding: ItemSettingSliderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindSlider(item: SettingItem.Slider) {
            binding.tvTitle.text = item.title
            binding.tvSummary.text = item.summary
            binding.tvValue.text = item.valueFromFlow().toString()
            val range = item.valueRange
            binding.seekBar.max = range.last - range.first
            binding.seekBar.progress = item.valueFromFlow() - range.first
            binding.seekBar.setOnSeekBarChangeListener(SliderListener(binding) { progress ->
                val value = progress + range.first
                binding.tvValue.text = value.toString()
                item.onValueChanged(value)
            })
        }

        fun bindSliderInt(item: SettingItem.SliderInt) {
            binding.tvTitle.text = item.title
            binding.tvSummary.text = item.summary
            binding.tvValue.text = item.displayFormat(item.valueFromFlow())
            val range = item.valueRange
            binding.seekBar.max = range.last - range.first
            binding.seekBar.progress = item.valueFromFlow() - range.first
            binding.seekBar.setOnSeekBarChangeListener(SliderListener(binding) { progress ->
                val value = progress + range.first
                binding.tvValue.text = item.displayFormat(value)
                item.onValueChanged(value)
            })
        }

        fun bindSliderFloat(item: SettingItem.SliderFloat) {
            binding.tvTitle.text = item.title
            binding.tvSummary.text = item.summary
            binding.tvValue.text = item.displayFormat(item.valueFromFlow())
            val range = item.floatRange
            binding.seekBar.max = item.floatSteps
            val ratio = ((item.valueFromFlow() - range.start) / (range.endInclusive - range.start))
                .coerceIn(0f, 1f)
            binding.seekBar.progress = (ratio * item.floatSteps).roundToInt()
            binding.seekBar.setOnSeekBarChangeListener(SliderListener(binding) { progress ->
                val value = range.start + (progress.toFloat() / item.floatSteps) *
                        (range.endInclusive - range.start)
                binding.tvValue.text = item.displayFormat(value)
                item.onValueChanged(value)
            })
        }

        private class SliderListener(
            private val binding: ItemSettingSliderBinding,
            private val onProgress: (Int) -> Unit
        ) : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onProgress(progress)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        }
    }
}

class SettingItemDiffCallback : DiffUtil.ItemCallback<SettingItem>() {
    override fun areItemsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean =
        oldItem.key == newItem.key

    override fun areContentsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean =
        oldItem == newItem
}
