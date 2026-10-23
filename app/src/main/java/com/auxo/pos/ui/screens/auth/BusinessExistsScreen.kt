// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.auth

import com.auxo.pos.ui.components.GlassButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.ui.theme.Spacing

/**
 * Shown when "Set up your business" is tapped on a device that already has one. It never offers to replace or reset
 * the existing business: it only sends the person back to sign in (where Forgot password is available).
 */
@Composable
fun BusinessExistsScreen(onBack: () -> Unit) {
    AuthLayout {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("business_exists")) {
            Text(stringResource(R.string.auth_exists_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.auth_exists_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlassButton(onClick = onBack, modifier = Modifier.testTag("business_exists_back")) {
                Text(stringResource(R.string.auth_back_to_login))
            }
        }
    }
}
