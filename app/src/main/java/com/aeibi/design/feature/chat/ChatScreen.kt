package com.aeibi.design.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aeibi.design.feature.chat.components.ChatComposer
import com.aeibi.design.feature.chat.components.ChatMessageList

@Composable
fun ChatScreen(projectId: String, sessionId: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ChatMessageList(
            projectId = projectId,
            sessionId = sessionId,
            modifier = Modifier.weight(1f)
        )
        ChatComposer()
    }
}
