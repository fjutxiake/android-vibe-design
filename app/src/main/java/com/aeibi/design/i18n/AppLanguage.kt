package com.aeibi.design.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(private val languageTag: String?) {
    SYSTEM(null),
    SIMPLIFIED_CHINESE("zh-CN"),
    ENGLISH("en");

    fun setAsCurrent() {
        val locales = languageTag
            ?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        fun current(): AppLanguage {
            val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return SYSTEM
            return when (locale.language) {
                "zh" -> SIMPLIFIED_CHINESE
                "en" -> ENGLISH
                else -> SYSTEM
            }
        }
    }
}
