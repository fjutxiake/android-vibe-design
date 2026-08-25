package com.aeibi.design.data.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** 按语言偏好包装 Context；SYSTEM 表示不覆盖、跟随系统解析资源。 */
fun Context.withLanguagePreference(preference: LanguagePreference): Context {
    if (preference == LanguagePreference.SYSTEM) return this
    val locale = preference.resolveLocale()
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}

/** 解析偏好对应的 locale；SYSTEM 返回设备当前系统 locale。 */
fun LanguagePreference.resolveLocale(): Locale = when (this) {
    LanguagePreference.SYSTEM -> Resources.getSystem().configuration.locales.get(0)
    LanguagePreference.ZH -> Locale("zh")
    LanguagePreference.EN -> Locale("en")
}

/**
 * 热更新该 Context 的资源 configuration 到指定语言偏好。
 *
 * 弹层/对话框等独立窗口继承 Activity 的 Context，不经过 Compose 的 LocalContext 覆盖；
 * 偏好变化时必须同步更新 Activity 自身资源，否则要等下次启动的 attachBaseContext 才生效。
 */
fun Context.applyLanguagePreference(preference: LanguagePreference) {
    val configuration = Configuration(resources.configuration)
    val locale = preference.resolveLocale()
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    resources.updateConfiguration(configuration, resources.displayMetrics)
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
