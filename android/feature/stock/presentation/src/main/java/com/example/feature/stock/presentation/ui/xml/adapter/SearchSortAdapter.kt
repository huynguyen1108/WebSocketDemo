package com.example.feature.stock.presentation.ui.xml.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.example.feature.stock.presentation.SortMode
import com.example.feature.stock.presentation.databinding.ItemSearchSortBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Single-item adapter for the search bar + sort button row.
 * Always visible (itemCount = 1).
 */
class SearchSortAdapter(
    private val onSearch: (String) -> Unit,
    private val onSort: (SortMode) -> Unit,
) : RecyclerView.Adapter<SearchSortAdapter.ViewHolder>() {

    private var currentQuery = ""

    fun updateQuery(query: String) {
        currentQuery = query
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    inner class ViewHolder(private val binding: ItemSearchSortBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.etSearch.doAfterTextChanged { onSearch(it?.toString() ?: "") }
            binding.btnSort.setOnClickListener { showSortDialog(binding.root.context) }
        }

        fun bind(query: String) {
            // Only update if text differs to avoid re-triggering the text watcher
            if (binding.etSearch.text?.toString() != query) {
                binding.etSearch.setText(query)
                binding.etSearch.setSelection(query.length)
            }
        }

        private fun showSortDialog(context: Context) {
            val labels = SortMode.entries.map { it.label }.toTypedArray()
            MaterialAlertDialogBuilder(context)
                .setTitle("Sắp xếp theo")
                .setItems(labels) { _, which -> onSort(SortMode.entries[which]) }
                .show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemSearchSortBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(currentQuery)
}
