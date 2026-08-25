package com.aeibi.design.data.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** 按语言偏好包装 Context；SYSTEM 表示不覆盖、跟随系统解析资源。 */
fun Context.withLanguagePreference(preference: LanguagePreference): Context {
    if (preference == LanguagePreference.SYSTEM) return this
    val locale = Locale(if (preference == LanguagePreference.ZH) "zh" else "en")
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}

/**
 * 供 Compose 覆盖 LocalContext 使用的包装器。
 *
 * base 保留 Activity 本身：hiltViewModel() 等组件会沿 baseContext 链解包寻找 Activity，
 * 若直接提供 createConfigurationContext 的结果会因链中无 Activity 而崩溃。
 * 字符串/文本资源则重定向到 [localized]，实现不重建 Activity 的即时换语言。
 */
class LocalizedContextWrapper(base: Context, private val localized: Context) : ContextWrapper(base) {
    // Context 的 getString/getText 是 final 且内部委托 getResources()，
    // 因此只需覆盖 getResources() 即可重定向全部字符串读取。
    override fun getResources(): Resources = localized.resources
}
