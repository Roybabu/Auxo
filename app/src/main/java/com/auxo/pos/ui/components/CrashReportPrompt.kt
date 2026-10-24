// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.components

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.auxo.pos.R
import com.auxo.pos.core.diagnostics.CrashReports
import androidx.compose.ui.Modifier

/**
 * After the app crashed, offers once to share the saved report. Choosing either button counts as asked, so it does not
 * come back for the same crash. Nothing is sent unless the person picks an app to share with.
 */
@Composable
fun CrashReportPrompt() {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.crash_prompt_send)
    val copied = stringResource(R.string.crash_github_copied)
    var report by remember { mutableStateOf(CrashReports.pending(context)) }
    val file = report ?: return
    AlertDialog(
        onDismissRequest = {
            CrashReports.markAsked(context, file)
            report = null
        },
        title = { Text(stringResource(R.string.crash_prompt_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.crash_prompt_body))
                TextButton(
                    onClick = {
                        CrashReports.markAsked(context, file)
                        report = null
                        runCatching {
                            val open = CrashReports.issueIntent(context, file)
                            android.widget.Toast.makeText(context, copied, android.widget.Toast.LENGTH_LONG).show()
                            context.startActivity(open)
                        }
                    },
                    modifier = Modifier.testTag("crash_prompt_github"),
                ) { Text(stringResource(R.string.crash_prompt_github)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    CrashReports.markAsked(context, file)
                    report = null
                    CrashReports.shareIntent(context, file)?.let { send ->
                        runCatching { context.startActivity(Intent.createChooser(send, chooserTitle)) }
                    }
                },
                modifier = Modifier.testTag("crash_prompt_send"),
            ) { Text(stringResource(R.string.crash_prompt_send)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    CrashReports.markAsked(context, file)
                    report = null
                },
                modifier = Modifier.testTag("crash_prompt_later"),
            ) { Text(stringResource(R.string.crash_prompt_later)) }
        },
    )
}
