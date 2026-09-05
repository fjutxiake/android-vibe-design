package com.aeibi.design.feature.chat.components

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

/**
 * Markdown 渲染文本——Markwon 引擎 + TextView 渲染（Markwon 的 span 是私有类型，
 * 自映射到 Compose AnnotatedString 等于重造渲染器；TextView 是其原生渲染面）。
 * 仅用于 assistant 完成态消息（流式/中间态保持纯文本）。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .build()
    }
    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            if (color != Color.Unspecified) {
                textView.setTextColor(color.toArgb())
            }
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.toSpValue())
            val lineScale = style.lineScale()
            if (lineScale > 0f) {
                textView.setLineSpacing(0f, lineScale)
            }
            markwon.setMarkdown(textView, text)
        },
        modifier = modifier
    )
}

private fun TextUnit.toSpValue(): Float = if (isSp) value else 14f

private fun TextStyle.lineScale(): Float =
    if (lineHeight.isSp && fontSize.isSp && lineHeight.value > 0f && fontSize.value > 0f) {
        lineHeight.value / fontSize.value
    } else {
        -1f
    }
