package com.aeibi.design.data.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 语言偏好的持久化存储。
 *
 * 刻意使用 SharedPreferences 而非 DataStore：语言偏好必须在 [Context.attachBaseContext]
 * 中同步读取（该时机早于任何 suspend 调用），这是 locale 偏好用 SP 的经典原因。
 *
 * 同时以 [changes] 暴露响应式状态：ViewModel 在 recreate 后依然存活，
 * 一次性快照读取会让 UI 停留在旧值，写入时必须推送新值。
 *
 * 三层覆盖各司其职：attachBaseContext 管 Activity 自身资源；Compose 根节点的
 * LocalContext 覆盖管主组合树；[applyToAppResources] 管弹层/对话框等独立窗口
 * （它们从应用级 Context 解析字符串，前两层都够不到）。
 */
@Singleton
class LanguagePreferenceStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val _changes = MutableStateFlow(readFrom(context))
    val changes: StateFlow<LanguagePreference> = _changes.asStateFlow()

    init {
        applyToAppResources(readFrom(context))
    }

    fun read(): LanguagePreference = _changes.value

    fun write(preference: LanguagePreference) {
        preferences(context).edit().putString(KEY_LANGUAGE, preference.tag).apply()
        _changes.value = preference
        applyToAppResources(preference)
    }

    /** 弹层/对话框窗口走应用级 Context，需同步更新应用资源的 configuration。 */
    private fun applyToAppResources(preference: LanguagePreference) {
        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        val locale = when (preference) {
            LanguagePreference.SYSTEM -> Resources.getSystem().configuration.locales.get(0)
            LanguagePreference.ZH -> Locale("zh")
            LanguagePreference.EN -> Locale("en")
        }
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    companion object {
        private const val PREFS_NAME = "language_prefs"
        private const val KEY_LANGUAGE = "language"

        /** attachBaseContext 早于 Hilt 注入，因此提供接 Context 的静态读取入口。 */
        fun readFrom(context: Context): LanguagePreference =
            LanguagePreference.fromTag(preferences(context).getString(KEY_LANGUAGE, null))

        private fun preferences(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
