package com.example.feature.stock.presentation.ui.xml

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.feature.chat.domain.model.ConnectionState
import com.example.feature.stock.presentation.StockUiState
import com.example.feature.stock.presentation.StockViewModel
import com.example.feature.stock.presentation.R
import com.example.feature.stock.presentation.databinding.FragmentStockXmlBinding
import com.example.feature.stock.presentation.ui.xml.adapter.IndicesRowAdapter
import com.example.feature.stock.presentation.ui.xml.adapter.SearchSortAdapter
import com.example.feature.stock.presentation.ui.xml.adapter.StockListAdapter
import com.example.feature.stock.presentation.ui.xml.adapter.SummaryAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StockXmlFragment : Fragment() {

    private var _binding: FragmentStockXmlBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StockViewModel by viewModels()

    // ── Adapters ──────────────────────────────────────────────────────────────

    private val indicesAdapter = IndicesRowAdapter()
    private val summaryAdapter = SummaryAdapter()
    private val searchSortAdapter by lazy {
        SearchSortAdapter(
            onSearch = viewModel::onSearchQueryChange,
            onSort = viewModel::onSortModeChange,
        )
    }
    private val stockListAdapter = StockListAdapter()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStockXmlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupConnectPanel()
        observeState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Detach adapter before nulling binding to avoid RecyclerView leaks
        binding.rvMarket.adapter = null
        _binding = null
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { requireActivity().finish() }
        binding.toolbar.inflateMenu(R.menu.menu_stock_xml)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_connect -> { viewModel.connect(); true }
                R.id.action_disconnect -> { viewModel.disconnect(); true }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        val concatConfig = ConcatAdapter.Config.Builder()
            .setIsolateViewTypes(true)
            .build()

        binding.rvMarket.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ConcatAdapter(concatConfig, indicesAdapter, summaryAdapter, searchSortAdapter, stockListAdapter)
            // Disable default change animations — causes flicker on rapid stock ticks
            itemAnimator = null
        }
    }

    private fun setupConnectPanel() {
        binding.btnConnect.setOnClickListener {
            val url = binding.etServerUrl.text?.toString()?.trim() ?: return@setOnClickListener
            viewModel.onServerUrlChange(url)
            viewModel.connect()
        }
    }

    // ── State observation ─────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: StockUiState) {
        renderConnectionState(state.connectionState, state.serverUrl)
        renderMarketData(state)
    }

    private fun renderConnectionState(connState: ConnectionState, serverUrl: String) {
        // Progress bar
        binding.progressConnecting.isVisible = connState is ConnectionState.Connecting

        // Connect panel visibility
        val showPanel = connState is ConnectionState.Idle
            || connState is ConnectionState.Disconnected
            || connState is ConnectionState.Error
        binding.connectPanel.isVisible = showPanel

        // Error message inside panel
        if (connState is ConnectionState.Error) {
            binding.tvError.isVisible = true
            binding.tvError.text = "⚠ ${connState.message}"
        } else {
            binding.tvError.isVisible = false
        }

        // Sync URL field (only when panel is visible and user hasn't typed)
        if (showPanel && binding.etServerUrl.text.isNullOrEmpty()) {
            binding.etServerUrl.setText(serverUrl)
        }

        // Toolbar subtitle with colored status
        updateToolbarSubtitle(connState)

        // Menu items
        val menu = binding.toolbar.menu
        when (connState) {
            is ConnectionState.Connected -> {
                menu.findItem(R.id.action_connect)?.isVisible = false
                menu.findItem(R.id.action_disconnect)?.isVisible = true
            }
            is ConnectionState.Connecting -> {
                menu.findItem(R.id.action_connect)?.isVisible = false
                menu.findItem(R.id.action_disconnect)?.isVisible = false
            }
            else -> {
                menu.findItem(R.id.action_connect)?.isVisible = true
                menu.findItem(R.id.action_disconnect)?.isVisible = false
            }
        }
    }

    private fun updateToolbarSubtitle(state: ConnectionState) {
        val text = when (state) {
            is ConnectionState.Connected -> "● LIVE"
            is ConnectionState.Connecting -> "● Đang kết nối..."
            is ConnectionState.Error -> "● Lỗi: ${state.message}"
            else -> "● Chưa kết nối"
        }
        val color = when (state) {
            is ConnectionState.Connected -> Color.parseColor("#00C853")
            is ConnectionState.Connecting -> Color.parseColor("#FFAB00")
            is ConnectionState.Error -> Color.parseColor("#FF1744")
            else -> Color.GRAY
        }
        binding.toolbar.subtitle = SpannableString(text).also {
            it.setSpan(ForegroundColorSpan(color), 0, it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun renderMarketData(state: StockUiState) {
        indicesAdapter.submitIndices(state.indices)
        summaryAdapter.update(state.stocks)
        searchSortAdapter.updateQuery(state.searchQuery)
        stockListAdapter.submitList(state.filteredStocks)
    }

    companion object {
        fun newInstance() = StockXmlFragment()
    }
}
