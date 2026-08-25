package com.aeibi.design

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeibi.design.data.i18n.LanguagePreferenceStore
import com.aeibi.design.data.i18n.LocalizedContextWrapper
import com.aeibi.design.data.i18n.applyLanguagePreference
import com.aeibi.design.data.i18n.withLanguagePreference
import com.aeibi.design.navigation.AppNavigation
import com.aeibi.design.theme.VibeDesignTheme
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var languagePreferenceStore: LanguagePreferenceStore

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withLanguagePreference(LanguagePreferenceStore.readFrom(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            // 语言切换不重建 Activity：在根节点用新 locale 的 Context 覆盖 LocalContext，
            // 全树原地重组合读取字符串，避免 recreate 的闪屏。
            // 包装源必须是 applicationContext：baseContext 在 attachBaseContext 时已被
            // 保存偏好包装过，用它切回 SYSTEM 会残留旧 locale；applicationContext 的资源
            // 始终跟随系统配置。
            val language by languagePreferenceStore.changes.collectAsStateWithLifecycle()
            val localeContext = remember(language) {
                LocalizedContextWrapper(this, applicationContext.withLanguagePreference(language))
            }

            // 弹层/对话框继承 Activity 的 Context，够不到 LocalContext 覆盖，
            // 偏好变化时热更新 Activity 自身资源，使其即时跟随。
            LaunchedEffect(language) {
                this@MainActivity.applyLanguagePreference(language)
            }

            CompositionLocalProvider(LocalContext provides localeContext) {
                VibeDesignTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}
