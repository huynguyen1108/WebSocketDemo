package com.example.feature.stock.presentation.ui.xml.adapter

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.feature.stock.domain.model.StockItem
import com.example.feature.stock.presentation.R
import com.example.feature.stock.presentation.databinding.ItemStockRowBinding

class StockListAdapter : ListAdapter<StockItem, StockListAdapter.ViewHolder>(DiffCallback()) {

    // ── ViewHolder ────────────────────────────────────────────────────────────

    inner class ViewHolder(private val binding: ItemStockRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(stock: StockItem) {
            binding.tvSymbol.text = stock.symbol
            binding.tvName.text = stock.name
            bindExchangeBadge(stock.exchange)
            bindPrice(stock)
        }

        fun bindPrice(stock: StockItem) {
            val ctx = itemView.context
            val color = when {
                stock.isUp -> ContextCompat.getColor(ctx, R.color.stock_up)
                stock.isDown -> ContextCompat.getColor(ctx, R.color.stock_down)
                else -> ContextCompat.getColor(ctx, R.color.stock_flat)
            }
            binding.tvPrice.text = formatPrice(stock.price)
            binding.tvPrice.setTextColor(color)

            val sign = if (stock.change >= 0) "+" else ""
            binding.tvChange.text = "$sign${formatPrice(stock.change)} ($sign${"%.2f".format(stock.changePercent)}%)"
            binding.tvChange.setTextColor(color)

            binding.tvVolume.text = "KL: ${formatVolume(stock.volume)}"
        }

        fun flash(isUp: Boolean) {
            val from = if (isUp) 0x2200C853.toInt() else 0x22FF1744.toInt()
            ObjectAnimator.ofObject(
                binding.flashOverlay,
                "backgroundColor",
                ArgbEvaluator(),
                from,
                Color.TRANSPARENT,
            ).apply {
                duration = 400
                start()
            }
        }

        private fun bindExchangeBadge(exchange: String) {
            val (bg, fg) = when (exchange.uppercase()) {
                "HNX" -> 0xFF1F2D4F.toInt() to 0xFF58A6FF.toInt()
                "UPCOM" -> 0xFF2D1F4F.toInt() to 0xFFBC8CFF.toInt()
                else -> 0xFF1F3D2F.toInt() to 0xFF00C853.toInt()
            }
            binding.tvExchange.text = exchange
            binding.tvExchange.setTextColor(fg)
            (binding.tvExchange.background.mutate() as? GradientDrawable)?.setColor(bg)
        }
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    data class PricePayload(val stock: StockItem, val isUp: Boolean)

    class DiffCallback : DiffUtil.ItemCallback<StockItem>() {
        override fun areItemsTheSame(oldItem: StockItem, newItem: StockItem) =
            oldItem.symbol == newItem.symbol

        override fun areContentsTheSame(oldItem: StockItem, newItem: StockItem) =
            oldItem == newItem

        override fun getChangePayload(oldItem: StockItem, newItem: StockItem): Any? =
            if (oldItem.price != newItem.price) PricePayload(newItem, newItem.price > oldItem.price)
            else null
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemStockRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    // Partial bind — only update price fields + trigger flash animation
    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) return super.onBindViewHolder(holder, position, payloads)
        val payload = payloads.last() as? PricePayload
            ?: return super.onBindViewHolder(holder, position, payloads)
        holder.bindPrice(payload.stock)
        holder.flash(payload.isUp)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun formatPrice(price: Double): String = "%,.0f".format(price)

fun formatVolume(vol: Long): String = when {
    vol >= 1_000_000 -> "${"%.1f".format(vol / 1_000_000.0)}M"
    vol >= 1_000 -> "${"%.0f".format(vol / 1_000.0)}K"
    else -> vol.toString()
}
