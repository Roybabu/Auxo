package com.auxo.pos.ui.screens.auth

import com.auxo.pos.ui.components.GlassButton
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.auxo.pos.domain.model.StoreSettings
import com.auxo.pos.ui.components.SectionHeader
import com.auxo.pos.ui.screens.billing.ExpiryWarningSection
import com.auxo.pos.ui.screens.billing.GstSection
import com.auxo.pos.ui.screens.billing.NumberingSection
import com.auxo.pos.ui.screens.billing.ReceiptSection
import com.auxo.pos.ui.screens.billing.StoreSection
import com.auxo.pos.ui.screens.billing.UpiSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.auth.SetupError
import com.auxo.pos.core.auth.SetupField
import com.auxo.pos.ui.components.AuxoIcon
import com.auxo.pos.ui.components.AuxoIconSize
import com.auxo.pos.ui.components.AuxoIconStyle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.auxo.pos.ui.components.AuxoTextField
import com.auxo.pos.ui.components.Illustration
import com.auxo.pos.ui.components.StrengthMeter
import com.auxo.pos.ui.components.SuggestionRow
import androidx.compose.ui.text.font.FontWeight
import com.auxo.pos.ui.components.crystalGlass
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.AdminSetupUiState
import com.auxo.pos.viewmodel.AdminSetupViewModel

@StringRes
internal fun SetupError.messageRes(): Int = when (this) {
    SetupError.BUSINESS_REQUIRED -> R.string.auth_error_business_required
    SetupError.BUSINESS_TOO_LONG -> R.string.auth_error_business_too_long
    SetupError.NAME_REQUIRED -> R.string.auth_error_name_required
    SetupError.NAME_TOO_LONG -> R.string.auth_error_name_too_long
    SetupError.IDENTIFIER_REQUIRED -> R.string.auth_error_identifier_required
    SetupError.IDENTIFIER_INVALID -> R.string.auth_error_identifier_invalid
    SetupError.IDENTIFIER_TAKEN -> R.string.auth_error_identifier_taken
    SetupError.PASSWORD_REQUIRED -> R.string.auth_error_password_required
    SetupError.PASSWORD_TOO_SHORT -> R.string.auth_error_password_short
    SetupError.PASSWORD_TOO_LONG -> R.string.auth_error_password_long
    SetupError.PASSWORD_NEEDS_LETTER_AND_DIGIT -> R.string.auth_error_password_mix
    SetupError.PASSWORD_TOO_COMMON -> R.string.auth_error_password_common
    SetupError.PASSWORD_CONTAINS_IDENTIFIER -> R.string.auth_error_password_identifier
    SetupError.CONFIRM_REQUIRED -> R.string.auth_error_confirm_required
    SetupError.CONFIRM_MISMATCH -> R.string.auth_error_confirm_mismatch
}

/** Shown once, on the first launch, until the administrator exists. */
@Composable
fun AdminSetupScreen(onBack: (() -> Unit)? = null, viewModel: AdminSetupViewModel = viewModel(factory = AdminSetupViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> viewModel.onLogoPicked(uri) }
    AdminSetupContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onIdentifierChange = viewModel::onIdentifierChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmChange = viewModel::onConfirmChange,
        onSubmit = viewModel::submit,
        onBack = onBack,
        onBusinessChange = viewModel::onBusinessChange,
        onSuggestionPick = viewModel::useSuggestion,
        onDetailsChange = viewModel::onDetailsChange,
        onGstinChange = viewModel::onGstinChange,
        onLogoChoose = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onLogoRemove = viewModel::onLogoRemoved,
    )
}

@Composable
fun AdminSetupContent(
    state: AdminSetupUiState,
    onNameChange: (String) -> Unit,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: (() -> Unit)? = null,
    onBusinessChange: (String) -> Unit = {},
    onSuggestionPick: (String) -> Unit = {},
    onDetailsChange: ((StoreSettings) -> StoreSettings) -> Unit = {},
    onGstinChange: (String) -> Unit = {},
    onLogoChoose: () -> Unit = {},
    onLogoRemove: () -> Unit = {},
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AuthLayout(
        backdropImage = R.drawable.auth_signup_bg,
        logoWidth = 220.dp,
        glass = true,
        // Outside the glass, below it: the same size and look as "Get started" on the sign-in page.
        footer = if (onBack == null) null else ({
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !state.isSubmitting,
                    shape = RoundedCornerShape(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    border = null,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .padding(horizontal = 1.dp)
                        .height(62.dp)
                        .crystalGlass(RoundedCornerShape(32.dp), rgb = true)
                        .testTag("setup_back"),
                ) {
                    AuxoIcon(R.drawable.ic_arrow_back, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.auth_back_to_login), color = AuthEmerald, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("admin_setup")) {
            Illustration(R.drawable.auth_storefront, modifier = Modifier.fillMaxWidth().height(110.dp))
            Text(stringResource(R.string.auth_setup_title), style = MaterialTheme.typography.headlineMedium, color = AuthInk)
            Text(
                stringResource(R.string.auth_setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AuxoTextField(
                value = state.businessName,
                onValueChange = onBusinessChange,
                label = stringResource(R.string.auth_field_business),
                error = state.errors[SetupField.BUSINESS]?.let { stringResource(it.messageRes()) },
                capitalization = KeyboardCapitalization.Words,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("setup_business"),
            )
            AuxoTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.auth_field_name_optional),
                error = state.errors[SetupField.NAME]?.let { stringResource(it.messageRes()) },
                capitalization = KeyboardCapitalization.Words,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("setup_name"),
            )
            AuxoTextField(
                value = state.identifier,
                onValueChange = onIdentifierChange,
                label = stringResource(R.string.auth_field_identifier),
                supportingText = stringResource(R.string.auth_field_identifier_hint),
                error = state.errors[SetupField.IDENTIFIER]?.let { stringResource(it.messageRes()) },
                keyboardType = KeyboardType.Email,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("setup_identifier"),
            )
            SuggestionRow(stringResource(R.string.auth_suggestions), state.suggestions, onSuggestionPick)
            AuxoTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.auth_field_password),
                supportingText = stringResource(R.string.auth_field_password_hint),
                error = state.errors[SetupField.PASSWORD]?.let { stringResource(it.messageRes()) },
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { PasswordToggle(showPassword) { showPassword = !showPassword } },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("setup_password"),
            )
            StrengthMeter(
                filled = state.strength,
                label = stringResource(
                    when (state.strength) {
                        1 -> R.string.auth_strength_weak
                        2 -> R.string.auth_strength_fair
                        3 -> R.string.auth_strength_good
                        else -> R.string.auth_strength_strong
                    },
                ),
            )
            AuxoTextField(
                value = state.confirm,
                onValueChange = onConfirmChange,
                label = stringResource(R.string.auth_field_confirm),
                error = state.errors[SetupField.CONFIRM]?.let { stringResource(it.messageRes()) },
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("setup_confirm"),
            )
            // Everything below is optional; only the shop name, username and password above are required.
            val detailErrors = if (state.submitted) state.detailErrors else emptyMap()
            val enabled = !state.isSubmitting
            Text(stringResource(R.string.auth_setup_details_title), style = MaterialTheme.typography.titleMedium, color = AuthInk)
            Text(
                stringResource(R.string.auth_setup_details_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("setup_details")) {
                SectionHeader(stringResource(R.string.store_logo_title))
                SetupLogoPicker(state.logo, enabled, onLogoChoose, onLogoRemove)
                SectionHeader(stringResource(R.string.settings_store))
                StoreSection(state.details, detailErrors, onDetailsChange, showName = false)
                SectionHeader(stringResource(R.string.settings_gst))
                GstSection(state.details, detailErrors, onDetailsChange, onGstinChange)
                SectionHeader(stringResource(R.string.settings_expiry_warning))
                ExpiryWarningSection(state.details, onDetailsChange)
                SectionHeader(stringResource(R.string.settings_numbering))
                NumberingSection(state.details, detailErrors, onDetailsChange)
                SectionHeader(stringResource(R.string.settings_upi))
                UpiSection(state.details, detailErrors, onDetailsChange)
                SectionHeader(stringResource(R.string.settings_receipt))
                ReceiptSection(state.details, detailErrors, onDetailsChange)
            }
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.auth_setup_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("setup_save_failed"),
                )
            }
            GlassButton(
                onClick = onSubmit,
                enabled = !state.isSubmitting,
                lit = true,
                modifier = Modifier.testTag("setup_submit"),
            ) {
                Text(stringResource(if (state.isSubmitting) R.string.auth_setup_working else R.string.auth_setup_action))
            }
            Text(
                stringResource(R.string.auth_setup_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
internal fun PasswordToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = Modifier.testTag("password_toggle")) {
        AuxoIcon(
            if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
            stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password),
            size = AuxoIconSize.Mini,
        )
    }
}

/** Choose / replace / remove the store logo on the setup form, with a small preview of the picture picked. */
@Composable
private fun SetupLogoPicker(logo: Uri?, enabled: Boolean, onChoose: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val preview by produceState<Bitmap?>(initialValue = null, logo) {
        value = if (logo == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(logo)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                    }
                }.getOrNull()
            }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().testTag("setup_logo"),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                // Only a picked logo gets a white plate; the empty placeholder lets the glass show through.
                .background(if (preview != null) Color.White else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = preview
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.store_logo_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(Spacing.xs),
                )
            } else {
                AuxoIcon(R.drawable.ic_image, contentDescription = null, size = AuxoIconSize.Large, style = AuxoIconStyle.Flat)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            OutlinedButton(onClick = onChoose, enabled = enabled, modifier = Modifier.testTag("setup_logo_choose")) {
                Text(stringResource(if (logo == null) R.string.store_logo_choose else R.string.store_logo_replace))
            }
            if (logo != null) {
                TextButton(onClick = onRemove, enabled = enabled, modifier = Modifier.testTag("setup_logo_remove")) {
                    Text(stringResource(R.string.store_logo_remove))
                }
            }
        }
    }
}
