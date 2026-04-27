package com.example.feature.stock.presentation.ui.xml.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.feature.stock.domain.model.MarketIndex
import com.example.feature.stock.presentation.databinding.ItemMarketIndicesRowBinding

/**
 * Single-item adapter that renders a horizontal RecyclerView of market index cards.
 * Hides itself (itemCount=0) until indices data arrives.
 */
class IndicesRowAdapter : RecyclerView.Adapter<IndicesRowAdapter.ViewHolder>() {

    private val innerAdapter = MarketIndexCardAdapter()
    private var hasData = false

    fun submitIndices(indices: List<MarketIndex>) {
        val wasEmpty = !hasData
        hasData = indices.isNotEmpty()
        innerAdapter.submitList(indices)
        when {
            wasEmpty && hasData -> notifyItemInserted(0)
            !wasEmpty && !hasData -> notifyItemRemoved(0)
            !wasEmpty && hasData -> notifyItemChanged(0)
        }
    }

    override fun getItemCount() = if (hasData) 1 else 0

    inner class ViewHolder(private val binding: ItemMarketIndicesRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.rvIndices.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = innerAdapter
                // Smooth scrolling + no flicker on updates
                itemAnimator = null
                addItemDecoration(HorizontalSpacingDecoration(8.dpToPx(context)))
            }
        }

        // No bind needed — inner adapter handles updates reactively
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemMarketIndicesRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = Unit
}
