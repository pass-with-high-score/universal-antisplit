package app.pwhs.universalantisplit.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anti_split_settings")

object PreferenceKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
    val THEME_PRESET = stringPreferencesKey("theme_preset")
    val AUTO_SIGN = booleanPreferencesKey("auto_sign")
    val BYPASS_SIGNATURE = booleanPreferencesKey("bypass_signature")
    val ALIGN_16KB = booleanPreferencesKey("align_16kb")
    val CLEAN_CACHE = booleanPreferencesKey("clean_cache")
    val OUTPUT_DIR = stringPreferencesKey("output_dir")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
}
