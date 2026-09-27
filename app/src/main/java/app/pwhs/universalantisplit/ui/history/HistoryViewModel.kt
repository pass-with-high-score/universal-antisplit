package app.pwhs.universalantisplit.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalantisplit.data.local.db.entity.ConversionHistory
import app.pwhs.universalantisplit.data.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistoryStatusFilter {
    ALL,
    SUCCESS,
    FAILED,
}

enum class HistorySourceFilter {
    ALL,
    INSTALLED,
    CONTAINER,
}

data class HistoryUiState(
    val allHistoryList: List<ConversionHistory> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: HistoryStatusFilter = HistoryStatusFilter.ALL,
    val sourceFilter: HistorySourceFilter = HistorySourceFilter.ALL,
    val isLoading: Boolean = true,
    val showClearConfirm: Boolean = false,
) {
    val filteredHistoryList: List<ConversionHistory>
        get() {
            return allHistoryList.filter { item ->
                val matchesQuery = if (searchQuery.isBlank()) {
                    true
                } else {
                    item.appName.contains(searchQuery, ignoreCase = true) ||
                        item.packageName.contains(searchQuery, ignoreCase = true) ||
                        item.outputPath.contains(searchQuery, ignoreCase = true)
                }
                val matchesStatus = when (statusFilter) {
                    HistoryStatusFilter.ALL -> true
                    HistoryStatusFilter.SUCCESS -> item.isSuccessful
                    HistoryStatusFilter.FAILED -> !item.isSuccessful
                }
                val matchesSource = when (sourceFilter) {
                    HistorySourceFilter.ALL -> true
                    HistorySourceFilter.INSTALLED -> item.sourceType == "INSTALLED"
                    HistorySourceFilter.CONTAINER -> item.sourceType != "INSTALLED"
                }
                matchesQuery && matchesStatus && matchesSource
            }
        }

    val isFilterActive: Boolean
        get() = searchQuery.isNotBlank() ||
            statusFilter != HistoryStatusFilter.ALL ||
            sourceFilter != HistorySourceFilter.ALL
}

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        historyRepository.getAllHistory()
            .onEach { list ->
                _uiState.update { it.copy(allHistoryList = list, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onStatusFilterChanged(filter: HistoryStatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    fun onSourceFilterChanged(filter: HistorySourceFilter) {
        _uiState.update { it.copy(sourceFilter = filter) }
    }

    fun resetFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                statusFilter = HistoryStatusFilter.ALL,
                sourceFilter = HistorySourceFilter.ALL
            )
        }
    }

    fun showClearConfirm(show: Boolean) {
        _uiState.update { it.copy(showClearConfirm = show) }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
            _uiState.update { it.copy(showClearConfirm = false) }
        }
    }

    fun deleteItem(item: ConversionHistory) {
        viewModelScope.launch {
            historyRepository.deleteHistory(item)
        }
    }
}
