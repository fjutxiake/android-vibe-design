package com.aeibi.design.feature.settings.ai

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.aeibi.design.ai.provider.ProviderConfig
import com.aeibi.design.theme.VibeDesignTheme
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class ProviderConfigEditorTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun save_isDisabledUntilExistingApiKeyLoads() {
        val apiKey = CompletableDeferred<String?>()
        composeTestRule.setContent {
            VibeDesignTheme(dynamicColor = false) {
                ProviderConfigEditor(
                    initialConfig = ProviderConfig(
                        id = UUID.randomUUID().toString(),
                        providerType = "deepseek",
                        displayName = "DeepSeek",
                        endpoint = "https://api.deepseek.com",
                        models = listOf("deepseek-v4-flash")
                    ),
                    providerName = "DeepSeek",
                    providerIconRes = null,
                    isSaving = false,
                    onDismiss = {},
                    onRevealApiKey = { apiKey.await() },
                    onSave = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("保存").assertIsNotEnabled()

        composeTestRule.runOnIdle { apiKey.complete("existing-key") }

        composeTestRule.onNodeWithText("保存").assertIsEnabled()
    }
}
