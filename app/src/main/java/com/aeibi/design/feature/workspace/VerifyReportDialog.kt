package com.aeibi.design.feature.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aeibi.design.R
import com.aeibi.design.data.verification.CheckResult
import com.aeibi.design.data.verification.CheckSeverity
import com.aeibi.design.data.verification.VerifyReport
import com.aeibi.design.theme.spacing

/** 验证报告对话框（ktlint 式检查结果：✅/⚠️/❌ 列表）。 */
@Composable
internal fun VerifyReportDialog(report: VerifyReport, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = if (report.passed) {
                    stringResource(R.string.verify_passed)
                } else {
                    stringResource(R.string.verify_failed, report.results.count { it.severity == CheckSeverity.ERROR })
                },
                color = if (report.passed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(report.results, key = { it.id }) { result ->
                    VerifyResultRow(result)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun VerifyResultRow(result: CheckResult) {
    val color = when (result.severity) {
        CheckSeverity.OK -> MaterialTheme.colorScheme.primary
        CheckSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
        CheckSeverity.ERROR -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        Text(text = result.severity.symbol, color = color)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
