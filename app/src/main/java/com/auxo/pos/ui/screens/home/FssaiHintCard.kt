package com.auxo.pos.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.ui.can
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.FssaiHintViewModel

/**
 * A reminder for the owner to add the shop's FSSAI number when it sells food or drink and none is set. Only
 * people who can change settings see it, and nothing is read for anyone else.
 */
@Composable
fun FssaiHintNotice(onOpenStoreDetails: () -> Unit) {
    if (!can(Permission.MANAGE_SETTINGS)) return
    val viewModel: FssaiHintViewModel = viewModel(factory = FssaiHintViewModel.Factory)
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    if (visible) FssaiHintCard(onClick = onOpenStoreDetails, onDismiss = viewModel::dismiss)
}

@Composable
fun FssaiHintCard(onClick: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).testTag("home_fssai_hint"),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.home_fssai_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.home_fssai_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("home_fssai_dismiss")) {
                    Text(stringResource(R.string.home_fssai_dismiss))
                }
            }
        }
    }
}
