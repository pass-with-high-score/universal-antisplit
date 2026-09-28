package app.pwhs.universalantisplit.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalantisplit.data.cache.AppCacheManager
import app.pwhs.universalantisplit.data.local.PreferenceKeys
import app.pwhs.universalantisplit.data.local.dataStore
import app.pwhs.universalantisplit.data.repository.HistoryRepository
import app.pwhs.universalantisplit.domain.AppLanguage
import app.pwhs.universalantisplit.domain.AppThemePreset
import app.pwhs.universalantisplit.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val amoledMode: Boolean = false,
    val themePreset: AppThemePreset = AppThemePreset.Orange,
    val autoSign: Boolean = true,
    val align16Kb: Boolean = true,
    val cleanCache: Boolean = true,
    val outputDir: String = "/sdcard/Download/UniversalAntiSplit",
    val appLanguage: AppLanguage = AppLanguage.System,
    val cacheSize: String = "0 B",
    val isClearingCache: Boolean = false,
)

class SettingsScreenViewModel(
    private val context: Context,
    private val appCacheManager: AppCacheManager,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            context.dataStore.data.collectLatest { prefs ->
                val modeName = prefs[PreferenceKeys.THEME_MODE] ?: ThemeMode.System.name
                val mode = ThemeMode.entries.find { it.name == modeName } ?: ThemeMode.System
                val dynamicColor = prefs[PreferenceKeys.DYNAMIC_COLOR] ?: false
                val amoledMode = prefs[PreferenceKeys.AMOLED_MODE] ?: false
                val presetName = prefs[PreferenceKeys.THEME_PRESET] ?: AppThemePreset.Orange.name
                val themePreset = AppThemePreset.entries.find { it.name == presetName } ?: AppThemePreset.Orange
                val autoSign = prefs[PreferenceKeys.AUTO_SIGN] ?: true
                val align16Kb = prefs[PreferenceKeys.ALIGN_16KB] ?: true
                val cleanCache = prefs[PreferenceKeys.CLEAN_CACHE] ?: true
                val outputDir = prefs[PreferenceKeys.OUTPUT_DIR] ?: "/sdcard/Download/UniversalAntiSplit"
                val appLanguage = AppLanguage.fromTag(prefs[PreferenceKeys.APP_LANGUAGE])

                _uiState.update {
                    it.copy(
                        themeMode = mode,
                        dynamicColor = dynamicColor,
                        amoledMode = amoledMode,
                        themePreset = themePreset,
                        autoSign = autoSign,
                        align16Kb = align16Kb,
                        cleanCache = cleanCache,
                        outputDir = outputDir,
                        appLanguage = appLanguage,
                    )
                }
            }
        }
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val breakdown = appCacheManager.getCacheBreakdown()
            _uiState.update { it.copy(cacheSize = breakdown.formattedTotalSize) }
        }
    }

    fun clearCache(includeLocalMerged: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingCache = true) }
            appCacheManager.clearCache(includeLocalMerged)
            val breakdown = appCacheManager.getCacheBreakdown()
            _uiState.update {
                it.copy(
                    isClearingCache = false,
                    cacheSize = breakdown.formattedTotalSize
                )
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.THEME_MODE] = mode.name
            }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.DYNAMIC_COLOR] = enabled
            }
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.AMOLED_MODE] = enabled
            }
        }
    }

    fun setThemePreset(preset: AppThemePreset) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.THEME_PRESET] = preset.name
            }
        }
    }

    fun setAutoSign(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.AUTO_SIGN] = enabled
            }
        }
    }

    fun setAlign16Kb(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.ALIGN_16KB] = enabled
            }
        }
    }

    fun setCleanCache(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.CLEAN_CACHE] = enabled
            }
        }
    }
}
