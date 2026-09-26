package app.pwhs.universalantisplit.ui.base

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.pwhs.universalantisplit.data.local.PreferenceKeys
import app.pwhs.universalantisplit.data.local.dataStore
import app.pwhs.universalantisplit.domain.AppThemePreset
import app.pwhs.universalantisplit.domain.ThemeMode
import app.pwhs.universalantisplit.theme.UniversalAntiSplitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

abstract class BaseActivity : ComponentActivity() {

    protected data class AppThemeState(
        val mode: ThemeMode = ThemeMode.System,
        val dynamicColor: Boolean = false,
        val amoledMode: Boolean = false,
        val themePreset: AppThemePreset = AppThemePreset.Orange,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    protected fun setContentWithTheme(content: @Composable () -> Unit) {
        enableEdgeToEdge()
        setContent {
            val initialState = remember {
                runBlocking {
                    val prefs = dataStore.data.first()
                    val modeName = prefs[PreferenceKeys.THEME_MODE] ?: ThemeMode.System.name
                    val mode = ThemeMode.entries.find { it.name == modeName } ?: ThemeMode.System
                    val dynamicColor = prefs[PreferenceKeys.DYNAMIC_COLOR] ?: false
                    val amoledMode = prefs[PreferenceKeys.AMOLED_MODE] ?: false
                    val presetName = prefs[PreferenceKeys.THEME_PRESET] ?: AppThemePreset.Orange.name
                    val themePreset = AppThemePreset.entries.find { it.name == presetName } ?: AppThemePreset.Orange
                    AppThemeState(mode, dynamicColor, amoledMode, themePreset)
                }
            }

            val themeStateFlow = remember {
                dataStore.data.map { prefs ->
                    val modeName = prefs[PreferenceKeys.THEME_MODE] ?: ThemeMode.System.name
                    val mode = ThemeMode.entries.find { it.name == modeName } ?: ThemeMode.System
                    val dynamicColor = prefs[PreferenceKeys.DYNAMIC_COLOR] ?: false
                    val amoledMode = prefs[PreferenceKeys.AMOLED_MODE] ?: false
                    val presetName = prefs[PreferenceKeys.THEME_PRESET] ?: AppThemePreset.Orange.name
                    val themePreset = AppThemePreset.entries.find { it.name == presetName } ?: AppThemePreset.Orange
                    AppThemeState(mode, dynamicColor, amoledMode, themePreset)
                }
            }
            val themeState by themeStateFlow.collectAsState(initial = initialState)

            val darkTheme = when (themeState.mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                )
                onDispose {}
            }

            UniversalAntiSplitTheme(
                darkTheme = darkTheme,
                dynamicColor = themeState.dynamicColor,
                amoledMode = themeState.amoledMode,
                themePreset = themeState.themePreset
            ) {
                content()
            }
        }
    }
}
