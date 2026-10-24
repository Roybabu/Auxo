// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.auxo.pos.R
import com.auxo.pos.core.debuglog.DebugLog
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.theme.Spacing

/** Everything meant for debugging: sharing the saved logs and a crash to try the crash report. Reached only in a debug build. */
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.debug_page_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                .testTag("debug_list"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DebugItem(
                R.drawable.ic_share,
                stringResource(R.string.debug_logs_title),
                stringResource(R.string.debug_logs_body),
                {
                    val send = DebugLog.shareIntent(context)
                    if (send != null) {
                        runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
                    }
                },
                modifier = Modifier.testTag("more_debug_logs"),
            )
            DebugItem(
                R.drawable.ic_error,
                stringResource(R.string.debug_crash_title),
                stringResource(R.string.debug_crash_body),
                { throw IllegalStateException("Test crash from the debug button") },
                modifier = Modifier.testTag("more_debug_crash"),
            )
        }
    }
}

@Composable
private fun DebugItem(@DrawableRes icon: Int, title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AuxoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            AuxoIcon(icon, contentDescription = null, size = AuxoIconSize.Medium)
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.lg)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
