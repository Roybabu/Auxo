// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.screens.more

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.core.appearance.AppAccent
import com.auxo.pos.core.appearance.AppearanceSettings
import com.auxo.pos.domain.model.Language
import com.auxo.pos.ui.components.AuxoCard
import com.auxo.pos.ui.components.AuxoTopAppBar
import com.auxo.pos.ui.theme.AccentPalette
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.viewmodel.AppearanceViewModel

/** Dark mode and the app colour. Changes apply at once and are saved. */
@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: AppearanceViewModel = viewModel(factory = AppearanceViewModel.Factory)) {
    val appearance by viewModel.settings.collectAsStateWithLifecycle()
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()
    AppearanceContent(
        appearance,
        onBack,
        viewModel::onAccentChange,
        viewModel::onDarkModeChange,
        appLanguage = language,
        onAppLanguageChange = { chosen ->
            viewModel.onAppLanguageChange(chosen)
            // The screens are rebuilt in the new language. The bill in progress is kept: it lives in the view model.
            activity?.recreate()
        },
    )
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AppearanceContent(
    appearance: AppearanceSettings,
    onBack: () -> Unit,
    onAccentChange: (AppAccent) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    /** The language chosen for the app (null follows the phone). The language card shows only when [onAppLanguageChange] is given. */
    appLanguage: Language? = null,
    onAppLanguageChange: ((Language?) -> Unit)? = null,
) {
    Scaffold(
        topBar = { AuxoTopAppBar(title = stringResource(R.string.appearance_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                .testTag("appearance_screen"),
        ) {
            AppearanceCard(appearance, onAccentChange, onDarkModeChange)
            if (onAppLanguageChange != null) {
                LanguageCard(appLanguage, onAppLanguageChange, modifier = Modifier.padding(top = Spacing.lg))
            }
        }
    }
}

/** Dark mode switch and the eight app colours. */
@Composable
internal fun AppearanceCard(
    appearance: AppearanceSettings,
    onAccentChange: (AppAccent) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
) {
    AuxoCard(modifier = Modifier.fillMaxWidth().testTag("appearance")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.appearance_dark_mode), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.appearance_dark_mode_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = appearance.darkMode,
                    onCheckedChange = onDarkModeChange,
                    modifier = Modifier.testTag("dark_mode_switch"),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Column {
                    Text(stringResource(R.string.appearance_colour), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.appearance_colour_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppAccent.entries.chunked(4).forEach { rowAccents ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        rowAccents.forEach { accent ->
                            AccentChoice(
                                accent = accent,
                                selected = accent == appearance.accent,
                                onClick = { onAccentChange(accent) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Which language the app's screens use: the phone's, or one chosen here. */
@Composable
internal fun LanguageCard(selected: Language?, onChange: (Language?) -> Unit, modifier: Modifier = Modifier) {
    AuxoCard(modifier = modifier.fillMaxWidth().testTag("language_card")) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.language_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LanguageRow(stringResource(R.string.language_phone), selected == null, "language_phone") { onChange(null) }
            Language.entries.forEach { language ->
                LanguageRow(language.nativeName, selected == language, "language_${language.tag}") { onChange(language) }
            }
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag(tag)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@StringRes
private fun AppAccent.nameRes(): Int = when (this) {
    AppAccent.EMERALD -> R.string.accent_emerald
    AppAccent.TEAL -> R.string.accent_teal
    AppAccent.OCEAN -> R.string.accent_ocean
    AppAccent.VIOLET -> R.string.accent_violet
    AppAccent.ORCHID -> R.string.accent_orchid
    AppAccent.ROSE -> R.string.accent_rose
    AppAccent.SUNSET -> R.string.accent_sunset
    AppAccent.SLATE -> R.string.accent_slate
}

/** One colour on the picker: a circle with a tick when chosen, and its name underneath. */
@Composable
private fun AccentChoice(accent: AppAccent, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(accent.nameRes())
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag("accent_${accent.id}")
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AccentPalette.swatch(accent))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
