package com.example.feature.stock.presentation.ui.xml.adapter

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.feature.stock.domain.model.MarketIndex
import com.example.feature.stock.presentation.R
import com.example.feature.stock.presentation.databinding.ItemMarketIndexCardBinding

class MarketIndexCardAdapter :
    ListAdapter<MarketIndex, MarketIndexCardAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemMarketIndexCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var lastChangeSign: Int = 0 // -1, 0, 1

        fun bind(index: MarketIndex) {
            binding.tvIndexName.text = index.name
            binding.tvIndexValue.text = "%.2f".format(index.value)

            val sign = if (index.change >= 0) "+" else ""
            binding.tvIndexChange.text = "$sign${"%.2f".format(index.change)} ($sign${"%.2f".format(index.changePercent)}%)"

            val ctx = itemView.context
            val color = when {
                index.isUp -> ContextCompat.getColor(ctx, R.color.stock_up)
                index.isDown -> ContextCompat.getColor(ctx, R.color.stock_down)
                else -> ContextCompat.getColor(ctx, R.color.stock_flat)
            }
            binding.tvIndexValue.setTextColor(color)
            binding.tvIndexChange.setTextColor(color)

            binding.tvAdvances.text = "↑${index.advances}"
            binding.tvDeclines.text = "↓${index.declines}"
            binding.tvNoChanges.text = "─${index.noChanges}"

            // Animate card background on direction change
            val newSign = when { index.isUp -> 1; index.isDown -> -1; else -> 0 }
            if (newSign != lastChangeSign) {
                animateCardBackground(index)
                lastChangeSign = newSign
            }
        }

        private fun animateCardBackground(index: MarketIndex) {
            val targetColor = when {
                index.isUp -> 0x1500C853.toInt()
                index.isDown -> 0x15FF1744.toInt()
                else -> ContextCompat.getColor(itemView.context, com.google.android.material.R.color.m3_sys_color_dynamic_dark_surface_variant)
            }
            ObjectAnimator.ofObject(
                binding.root,
                "cardBackgroundColor",
                ArgbEvaluator(),
                (binding.root.cardBackgroundColor?.defaultColor ?: 0xFF1E1E1E.toInt()),
                targetColor,
            ).apply {
                duration = 600
                start()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MarketIndex>() {
        override fun areItemsTheSame(oldItem: MarketIndex, newItem: MarketIndex) =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: MarketIndex, newItem: MarketIndex) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemMarketIndexCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}
