package app.pwhs.universalantisplit.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalantisplit.data.local.PreferenceKeys
import app.pwhs.universalantisplit.data.local.dataStore
import app.pwhs.universalantisplit.domain.AppLanguage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LanguageUiState(
    val selected: AppLanguage = AppLanguage.System,
)

class LanguageViewModel(
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanguageUiState())
    val uiState: StateFlow<LanguageUiState> = _uiState.asStateFlow()

    private val _recreateEvent = MutableSharedFlow<Unit>()
    val recreateEvent: SharedFlow<Unit> = _recreateEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            context.dataStore.data.collectLatest { prefs ->
                val language = AppLanguage.fromTag(prefs[PreferenceKeys.APP_LANGUAGE])
                _uiState.update { it.copy(selected = language) }
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        if (language == _uiState.value.selected) return
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.APP_LANGUAGE] = language.tag
            }
            _recreateEvent.emit(Unit)
        }
    }
}
