package app.pwhs.universalantisplit.data.local

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

object LocaleManager {

    fun wrap(base: Context): Context {
        val tag = persistedTag(base)
        if (tag.isEmpty()) return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    private fun persistedTag(base: Context): String = runBlocking {
        base.dataStore.data.first()[PreferenceKeys.APP_LANGUAGE].orEmpty()
    }
}
