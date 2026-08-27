package com.aeibi.design.feature.build

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R
import com.aeibi.design.theme.spacing

@Composable
fun BuildLog(modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing

    Text(
        text = stringResource(R.string.build_log_empty),
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.lg),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
