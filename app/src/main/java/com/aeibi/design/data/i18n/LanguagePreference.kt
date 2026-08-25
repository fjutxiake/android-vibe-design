package com.aeibi.design.data.i18n

enum class LanguagePreference(val tag: String) {
    SYSTEM("system"),
    ZH("zh"),
    EN("en")
    ;

    companion object {
        fun fromTag(tag: String?): LanguagePreference = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}
