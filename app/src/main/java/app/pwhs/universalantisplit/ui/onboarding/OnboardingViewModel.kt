package app.pwhs.universalantisplit.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalantisplit.data.local.PreferenceKeys
import app.pwhs.universalantisplit.data.local.dataStore
import app.pwhs.universalantisplit.domain.AppLanguage
import app.pwhs.universalantisplit.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val hasNotificationPermission: Boolean = false,
    val hasStoragePermission: Boolean = true,
    val hasInstallPermission: Boolean = false,
    val selectedLanguage: AppLanguage = AppLanguage.System,
    val selectedThemeMode: ThemeMode = ThemeMode.System,
    val isDynamicColor: Boolean = false,
    val isAmoledMode: Boolean = false,
    val isCompleted: Boolean = false,
)

class OnboardingViewModel(
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        refreshPermissionStates()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            val modeName = prefs[PreferenceKeys.THEME_MODE] ?: ThemeMode.System.name
            val themeMode = runCatching { ThemeMode.valueOf(modeName) }.getOrDefault(ThemeMode.System)
            val langTag = prefs[PreferenceKeys.APP_LANGUAGE]
            val language = AppLanguage.fromTag(langTag)

            _uiState.update {
                it.copy(
                    selectedThemeMode = themeMode,
                    selectedLanguage = language,
                    isDynamicColor = prefs[PreferenceKeys.DYNAMIC_COLOR] ?: false,
                    isAmoledMode = prefs[PreferenceKeys.AMOLED_MODE] ?: false,
                    isCompleted = prefs[PreferenceKeys.ONBOARDING_COMPLETED] ?: false
                )
            }
        }
    }

    fun refreshPermissionStates() {
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasStorage = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val hasInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

        _uiState.update {
            it.copy(
                hasNotificationPermission = hasNotification,
                hasStoragePermission = hasStorage,
                hasInstallPermission = hasInstall,
            )
        }
    }

    fun setLanguage(language: AppLanguage) {
        _uiState.update { it.copy(selectedLanguage = language) }
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.APP_LANGUAGE] = language.tag
            }
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        _uiState.update { it.copy(selectedThemeMode = themeMode) }
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.THEME_MODE] = themeMode.name
            }
        }
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[PreferenceKeys.ONBOARDING_COMPLETED] = true
            }
            _uiState.update { it.copy(isCompleted = true) }
            onSuccess()
        }
    }
}
