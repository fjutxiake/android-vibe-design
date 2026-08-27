package com.aeibi.design.feature.projects

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toDrawable
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.error
import coil3.request.fallback
import com.aeibi.design.R
import com.aeibi.design.data.projects.Project
import com.aeibi.design.theme.dimensions
import com.aeibi.design.theme.spacing
import com.aeibi.design.theme.systemAppIconShape
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun ProjectListItem(project: Project, onClick: () -> Unit = {}, onLongClick: () -> Unit = {}) {
    val context = LocalContext.current
    val spacing = MaterialTheme.spacing
    val dimensions = MaterialTheme.dimensions
    val shape = MaterialTheme.shapes.small
    val appIconShape = systemAppIconShape()

    val defaultIcon =
        if (LocalInspectionMode.current) {
            Color.LTGRAY.toDrawable()
        } else {
            context.packageManager.defaultActivityIcon
        }

    Row(
        modifier =
        Modifier.fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surface)
            .border(dimensions.borderThin, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        AsyncImage(
            model =
            ImageRequest.Builder(context)
                .data(project.iconUri)
                .fallback(defaultIcon)
                .error(defaultIcon)
                .build(),
            contentDescription = "${project.name} App Icon",
            modifier = Modifier.size(dimensions.projectListIcon).clip(appIconShape),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = project.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatRelativeTimeText(project.updatedAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** 按当前语言偏好把相对时间档位解析为展示文本;日期档用语言对应的 pattern 格式化。 */
@Composable
private fun formatRelativeTimeText(epochMillis: Long): String {
    val relativeTime = remember(epochMillis) { relativeTimeOf(epochMillis) }
    return when (relativeTime) {
        RelativeTime.JustNow -> stringResource(R.string.time_just_now)
        is RelativeTime.MinutesAgo -> stringResource(R.string.time_minutes_ago, relativeTime.count)
        is RelativeTime.HoursAgo -> stringResource(R.string.time_hours_ago, relativeTime.count)
        is RelativeTime.DaysAgo -> stringResource(R.string.time_days_ago, relativeTime.count)
        is RelativeTime.AbsoluteDate -> {
            val pattern = stringResource(R.string.time_absolute_date_pattern)
            val locale = LocalConfiguration.current.locales[0]
            remember(pattern, locale, relativeTime.epochMillis) {
                SimpleDateFormat(pattern, locale).format(Date(relativeTime.epochMillis))
            }
        }
    }
}
