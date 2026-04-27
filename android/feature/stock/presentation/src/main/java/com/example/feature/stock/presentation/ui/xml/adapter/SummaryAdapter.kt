package com.example.feature.stock.presentation.ui.xml.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.feature.stock.domain.model.StockItem
import com.example.feature.stock.presentation.databinding.ItemMarketSummaryBinding

/**
 * Single-item adapter for the market advances/declines summary bar.
 * Hides itself until stock data arrives.
 */
class SummaryAdapter : RecyclerView.Adapter<SummaryAdapter.ViewHolder>() {

    private var stocks: List<StockItem> = emptyList()

    fun update(newStocks: List<StockItem>) {
        val wasEmpty = stocks.isEmpty()
        stocks = newStocks
        when {
            wasEmpty && newStocks.isNotEmpty() -> notifyItemInserted(0)
            !wasEmpty && newStocks.isEmpty() -> notifyItemRemoved(0)
            !wasEmpty && newStocks.isNotEmpty() -> notifyItemChanged(0)
        }
    }

    override fun getItemCount() = if (stocks.isEmpty()) 0 else 1

    inner class ViewHolder(private val binding: ItemMarketSummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(stocks: List<StockItem>) {
            val ups = stocks.count { it.isUp }
            val downs = stocks.count { it.isDown }
            val flats = stocks.size - ups - downs
            binding.tvSummaryUp.text = "↑ $ups"
            binding.tvSummaryFlat.text = "─ $flats"
            binding.tvSummaryDown.text = "↓ $downs"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemMarketSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(stocks)
}
