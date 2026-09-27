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

data class HistoryUiState(
    val historyList: List<ConversionHistory> = emptyList(),
    val isLoading: Boolean = true,
    val showClearConfirm: Boolean = false,
)

class HistoryViewModel(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        historyRepository.getAllHistory()
            .onEach { list ->
                _uiState.update { it.copy(historyList = list, isLoading = false) }
            }
            .launchIn(viewModelScope)
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
