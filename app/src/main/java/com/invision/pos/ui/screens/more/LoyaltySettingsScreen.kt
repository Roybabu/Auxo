package com.invision.pos.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invision.pos.R
import com.invision.pos.domain.loyalty.LoyaltyMath
import com.invision.pos.domain.loyalty.LoyaltyRuleError
import com.invision.pos.domain.loyalty.returnPercentText
import com.invision.pos.domain.model.Money
import com.invision.pos.ui.components.InvisionCard
import com.invision.pos.ui.components.InvisionTextField
import com.invision.pos.ui.components.InvisionTopAppBar
import com.invision.pos.ui.format.LocalMoneyFormatter
import com.invision.pos.ui.theme.Spacing
import com.invision.pos.viewmodel.LoyaltySettingsUiState
import com.invision.pos.viewmodel.LoyaltySettingsViewModel

/** Loyalty points: on or off, what a point earns and what it is worth. Super Admin only. */
@Composable
fun LoyaltySettingsScreen(onBack: () -> Unit, viewModel: LoyaltySettingsViewModel = viewModel(factory = LoyaltySettingsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoyaltySettingsContent(
        state = state,
        onBack = onBack,
        onEnabledChange = viewModel::onEnabledChange,
        onSpendChange = viewModel::onSpendChange,
        onValueChange = viewModel::onValueChange,
        onSave = viewModel::save,
        onRetry = viewModel::retry,
    )
}

@Composable
fun LoyaltySettingsContent(
    state: LoyaltySettingsUiState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSpendChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    val formatter = LocalMoneyFormatter.current
    Scaffold(
        topBar = { InvisionTopAppBar(title = stringResource(R.string.loyalty_settings_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                .testTag("loyalty_settings"),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (state.loadFailed) {
                Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("loyalty_load_failed"))
                TextButton(onClick = onRetry, modifier = Modifier.testTag("loyalty_retry")) { Text(stringResource(R.string.action_retry)) }
                return@Column
            }
            if (state.isLoading) return@Column

            Text(stringResource(R.string.loyalty_settings_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            InvisionCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.loyalty_settings_switch), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(if (state.enabled) R.string.loyalty_settings_on else R.string.loyalty_settings_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("loyalty_state"),
                        )
                    }
                    Switch(checked = state.enabled, onCheckedChange = onEnabledChange, modifier = Modifier.testTag("loyalty_switch"))
                }
            }

            InvisionTextField(
                value = state.spendText,
                onValueChange = onSpendChange,
                label = stringResource(R.string.loyalty_settings_spend),
                error = fieldError(
                    state,
                    shown = state.spendText.isNotBlank(),
                    LoyaltyRuleError.SPEND_REQUIRED to R.string.error_required,
                    LoyaltyRuleError.SPEND_INVALID to R.string.error_invalid_amount,
                    LoyaltyRuleError.SPEND_OUT_OF_RANGE to R.string.loyalty_error_spend_range,
                ),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth().testTag("loyalty_spend"),
            )
            InvisionTextField(
                value = state.valueText,
                onValueChange = onValueChange,
                label = stringResource(R.string.loyalty_settings_value),
                error = fieldError(
                    state,
                    shown = state.valueText.isNotBlank(),
                    LoyaltyRuleError.VALUE_REQUIRED to R.string.error_required,
                    LoyaltyRuleError.VALUE_INVALID to R.string.error_invalid_amount,
                    LoyaltyRuleError.VALUE_OUT_OF_RANGE to R.string.loyalty_error_value_range,
                    LoyaltyRuleError.VALUE_ABOVE_SPEND to R.string.loyalty_error_value_above_spend,
                ),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth().testTag("loyalty_value"),
            )

            val rules = state.preview
            if (rules != null) {
                val bill = Money(100_000)
                val earned = LoyaltyMath.earned(rules.copy(enabled = true), bill)
                Text(
                    stringResource(
                        R.string.loyalty_settings_example,
                        formatter.format(bill),
                        earned,
                        formatter.format(LoyaltyMath.valueOf(rules, earned)),
                        rules.returnPercentText(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("loyalty_example"),
                )
            }
            Text(
                stringResource(R.string.loyalty_settings_rules_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = onSave, enabled = state.dirty && !state.isSaving, modifier = Modifier.fillMaxWidth().testTag("loyalty_save")) {
                Text(stringResource(R.string.action_save))
            }
            if (state.saved) {
                Text(stringResource(R.string.loyalty_settings_saved), color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("loyalty_saved"))
            }
            if (state.saveFailed) {
                Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("loyalty_save_failed"))
            }
        }
    }
}

@Composable
private fun fieldError(state: LoyaltySettingsUiState, shown: Boolean, vararg messages: Pair<LoyaltyRuleError, Int>): String? {
    if (!shown && !state.showErrors) return null
    val match = messages.firstOrNull { it.first in state.errors } ?: return null
    return stringResource(match.second)
}
