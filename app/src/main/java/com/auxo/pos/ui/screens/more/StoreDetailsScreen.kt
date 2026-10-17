package com.auxo.pos.ui.screens.more

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.screens.billing.BillingSettingsScreen
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.StoreLogoUiState
import com.auxo.pos.viewmodel.StoreLogoViewModel

/**
 * The shop's billing settings (name, address, GST, UPI, receipts) with the store logo above them.
 * This is the only place the billing settings live.
 */
@Composable
fun StoreDetailsScreen(
    onBack: () -> Unit,
    logoViewModel: StoreLogoViewModel = viewModel(factory = StoreLogoViewModel.Factory),
) {
    val logoState by logoViewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> logoViewModel.onPicked(uri) }
    BillingSettingsScreen(
        onBack = onBack,
        topSection = {
            StoreLogoSection(
                state = logoState,
                onChoose = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = logoViewModel::onRemove,
            )
        },
    )
}

/** Preview of the logo (or a placeholder) with choose / replace and remove. */
@Composable
fun StoreLogoSection(
    state: StoreLogoUiState,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuxoCard(modifier = modifier.fillMaxWidth().testTag("store_logo_section")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.store_logo_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.store_logo_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LogoPreview(state.logo)
            if (state.unreadable) {
                Text(
                    stringResource(R.string.store_logo_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("store_logo_error"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onChoose, enabled = !state.isWorking, modifier = Modifier.testTag("store_logo_choose")) {
                    Text(stringResource(if (state.logo == null) R.string.store_logo_choose else R.string.store_logo_replace))
                }
                if (state.logo != null) {
                    OutlinedButton(onClick = onRemove, enabled = !state.isWorking, modifier = Modifier.testTag("store_logo_remove")) {
                        Text(stringResource(R.string.store_logo_remove))
                    }
                }
            }
        }
    }
}

@Composable
private fun LogoPreview(logo: Bitmap?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (logo != null) Color.White else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                bitmap = logo.asImageBitmap(),
                contentDescription = stringResource(R.string.store_logo_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(Spacing.sm)
                    .testTag("store_logo_preview"),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("store_logo_placeholder")) {
                AuxoIcon(R.drawable.ic_image, contentDescription = null, size = AuxoIconSize.Large)
                Text(
                    stringResource(R.string.store_logo_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
