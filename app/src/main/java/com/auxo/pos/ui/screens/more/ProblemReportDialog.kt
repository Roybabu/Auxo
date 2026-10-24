// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.core.diagnostics.CrashReports

/**
 * The one "Report a problem" row on More opens this: send a diagnostic report to share with the developer, or report on
 * GitHub (a public issue). Either way nothing is sent until the person finishes the step in the app they pick.
 */
@Composable
fun ProblemReportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.crash_prompt_send)
    val copied = stringResource(R.string.crash_github_copied)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.help_problem_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ReportChoice(
                    title = stringResource(R.string.help_report_title),
                    body = stringResource(R.string.help_report_body),
                    modifier = Modifier.testTag("report_choice_diagnostic"),
                ) {
                    onDismiss()
                    CrashReports.diagnosticShare(context)?.let { send ->
                        runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
                    }
                }
                ReportChoice(
                    title = stringResource(R.string.help_github_title),
                    body = stringResource(R.string.help_github_body),
                    modifier = Modifier.testTag("report_choice_github"),
                ) {
                    onDismiss()
                    runCatching {
                        val open = CrashReports.issueIntent(context, CrashReports.diagnosticFile(context))
                        Toast.makeText(context, copied, Toast.LENGTH_LONG).show()
                        context.startActivity(open)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("report_choice_cancel")) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ReportChoice(title: String, body: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
