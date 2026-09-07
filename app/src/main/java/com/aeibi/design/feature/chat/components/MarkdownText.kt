package com.aeibi.design.feature.chat.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import kotlinx.coroutines.channels.Channel

/**
 * Markdown 渲染——multiplatform-markdown-renderer（Compose 原生、异步解析）。
 * 完成态文本用 [MarkdownText]；流式文本用 [StreamingMarkdownText]。
 *
 * 列表为 Column（非虚拟化）：每条消息只组合一次——完成态直接全文渲染，
 * 无需组合外解析缓存（无重挂载场景）。
 */

/** 完成态渲染：全文一次性异步解析（消息常驻组合，只发生一次）。 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified
) {
    val textColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
    Markdown(
        content = text,
        modifier = modifier,
        colors = markdownColor(text = textColor),
        typography = markdownTypography(text = style)
    )
}

/**
 * 流式渲染：每次收到 `textDelta`（数据层增量，文本连续性由事件保证）
 * append 进 StreamingMarkdownState——库只渲染**已闭合块**（stable AST），
 * 进行中的尾部保持纯文本；stable 部分 Compose 差分跳过，只组合新增。
 */
@Composable
fun StreamingMarkdownText(
    textDelta: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified
) {
    val textColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
    val state = rememberStreamingMarkdownState()
    // 顺序消费 channel——避免 LaunchedEffect(delta) 重启在 append 挂起点取消导致丢 chunk
    val deltaChannel = remember { Channel<String>(Channel.UNLIMITED) }
    LaunchedEffect(Unit) {
        for (chunk in deltaChannel) {
            state.append(chunk)
        }
    }
    LaunchedEffect(textDelta) {
        if (textDelta.isNotEmpty()) {
            deltaChannel.trySend(textDelta)
        }
    }
    Markdown(
        streamingMarkdownState = state,
        modifier = modifier,
        colors = markdownColor(text = textColor),
        typography = markdownTypography(text = style)
    )
}
