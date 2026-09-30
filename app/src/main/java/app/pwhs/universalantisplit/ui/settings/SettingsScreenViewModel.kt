package app.pwhs.universalantisplit.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

import android.net.Uri
import app.pwhs.universalantisplit.domain.KeystoreInfo
import app.pwhs.universalantisplit.engine.signer.ApkSignerManager
import app.pwhs.universalantisplit.R
import timber.log.Timber

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val amoledMode: Boolean = false,
    val themePreset: AppThemePreset = AppThemePreset.Orange,
    val autoSign: Boolean = true,
    val bypassSignature: Boolean = false,
    val align16Kb: Boolean = true,
    val cleanCache: Boolean = true,
    val outputDir: String = "/sdcard/Download/UniversalAntiSplit",
    val appLanguage: AppLanguage = AppLanguage.System,
    val keystoreInfo: KeystoreInfo? = null,
    val isKeystoreLoading: Boolean = false,
    val keystoreMessage: String? = null,
)

class SettingsScreenViewModel(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    private val apkSignerManager: ApkSignerManager,
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
                val bypassSignature = prefs[PreferenceKeys.BYPASS_SIGNATURE] ?: false
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
                        bypassSignature = bypassSignature,
                        align16Kb = align16Kb,
                        cleanCache = cleanCache,
                        outputDir = outputDir,
                        appLanguage = appLanguage,
                    )
                }
            }
        }
        loadKeystoreInfo()
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

    fun setBypassSignature(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.BYPASS_SIGNATURE] = enabled
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

    fun loadKeystoreInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isKeystoreLoading = true) }
            val info = apkSignerManager.getKeystoreInfo()
            _uiState.update { it.copy(keystoreInfo = info, isKeystoreLoading = false) }
        }
    }

    fun exportKeystore(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isKeystoreLoading = true) }
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    apkSignerManager.exportKeystore(output).getOrThrow()
                } ?: throw IllegalStateException("Could not open output stream")
            }
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isKeystoreLoading = false,
                        keystoreMessage = context.getString(R.string.keystore_export_success)
                    )
                }
            } else {
                Timber.e(result.exceptionOrNull(), "Failed to export keystore")
                _uiState.update {
                    it.copy(
                        isKeystoreLoading = false,
                        keystoreMessage = context.getString(
                            R.string.keystore_export_error,
                            result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                        )
                    )
                }
            }
        }
    }

    fun importKeystore(uri: Uri, password: String? = null, alias: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isKeystoreLoading = true) }
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    apkSignerManager.importKeystore(input, password, alias).getOrThrow()
                } ?: throw IllegalStateException("Could not open input stream")
            }
            result.onSuccess { newInfo ->
                _uiState.update {
                    it.copy(
                        keystoreInfo = newInfo,
                        isKeystoreLoading = false,
                        keystoreMessage = context.getString(R.string.keystore_import_success)
                    )
                }
            }.onFailure { error ->
                Timber.e(error, "Failed to import keystore")
                _uiState.update {
                    it.copy(
                        isKeystoreLoading = false,
                        keystoreMessage = context.getString(
                            R.string.keystore_import_error,
                            error.localizedMessage ?: "Unknown error"
                        )
                    )
                }
            }
        }
    }

    fun resetKeystore() {
        viewModelScope.launch {
            _uiState.update { it.copy(isKeystoreLoading = true) }
            apkSignerManager.resetToDefaultKeystore()
                .onSuccess { info ->
                    _uiState.update { it.copy(keystoreInfo = info, isKeystoreLoading = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isKeystoreLoading = false) }
                }
        }
    }

    fun clearKeystoreMessage() {
        _uiState.update { it.copy(keystoreMessage = null) }
    }
}
