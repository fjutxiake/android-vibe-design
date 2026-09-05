package com.aeibi.design.feature.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.aeibi.design.R
import com.aeibi.design.feature.chat.ChatTimelineItem
import com.aeibi.design.theme.spacing

/** 预览加载失败报告——折叠块（收起 = 摘要，展开 = 时间轴 + err 表格）。 */
@Composable
fun ErrorReportItem(item: ChatTimelineItem.ErrorReport, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(horizontal = spacing.sm)
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }
        ) {
            Text(
                text = item.summary,
                modifier = Modifier.weight(1f).padding(end = spacing.xs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = stringResource(
                    if (expanded) R.string.chat_cd_collapse_thinking else R.string.chat_cd_expand_thinking
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs)
            )
        }
    }
}
