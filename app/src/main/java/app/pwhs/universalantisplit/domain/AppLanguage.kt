package app.pwhs.universalantisplit.domain

import androidx.annotation.StringRes
import app.pwhs.universalantisplit.R

enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    System("", R.string.language_system_default),
    Vietnamese("vi", R.string.language_name_vi),
    English("en", R.string.language_name_en),
    Chinese("zh", R.string.language_name_zh),
    Korean("ko", R.string.language_name_ko),
    Spanish("es", R.string.language_name_es),
    Portuguese("pt", R.string.language_name_pt),
    PortugueseBrazil("pt-BR", R.string.language_name_pt_br),
    Russian("ru", R.string.language_name_ru),
    Arabic("ar", R.string.language_name_ar),
    French("fr", R.string.language_name_fr);

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.find { it.tag == tag } ?: System
    }
}
