// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.ui.theme.AuxoThemeExtras
import com.auxo.pos.ui.theme.Spacing
import com.auxo.pos.ui.theme.ToneColors

/** A rounded tile showing one headline figure, such as stock value or low-stock count. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    tone: ToneColors? = null,
    onClick: (() -> Unit)? = null,
) {
    val container = tone?.container ?: MaterialTheme.colorScheme.surface
    val content = tone?.content ?: MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        border = if (tone == null) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (iconRes != null) {
                    AuxoIcon(iconRes, contentDescription = null, size = AuxoIconSize.Mini, accent = tone?.accent)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(text = value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        }
    }
}

/** Small rounded label for statuses such as "Low stock". */
@Composable
fun StatusChip(text: String, tone: ToneColors, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = tone.container,
        contentColor = tone.content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = Spacing.xs),
        )
    }
}

/** Title for a group of content, with an optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        action?.invoke()
    }
}

/** Rounded white card used for list rows and form sections. */
@Composable
fun AuxoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/** A red notice for expired stock; [text] says how many items and what to do. */
@Composable
fun ExpiredStockBanner(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val tone = AuxoThemeExtras.statusColors.danger
    Surface(
        shape = MaterialTheme.shapes.large,
        color = tone.container,
        contentColor = tone.content,
        modifier = modifier.semantics(mergeDescendants = true) {}.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}
