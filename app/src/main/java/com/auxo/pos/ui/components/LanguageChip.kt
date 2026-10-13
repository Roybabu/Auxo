package com.auxo.pos.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auxo.pos.R
import com.auxo.pos.domain.model.Language
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.AuthMint
import com.auxo.pos.viewmodel.AppearanceViewModel

/**
 * The language button on the sign-in and introduction pages: a small pill with a globe and the language now in use. It opens a
 * short list, and the choice is the same one the Appearance page saves, so it applies to the whole app straight away.
 */
@Composable
fun LanguageChip(modifier: Modifier = Modifier, viewModel: AppearanceViewModel = viewModel(factory = AppearanceViewModel.Factory)) {
    val chosen by viewModel.appLanguage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The language the screens are in now: the chosen one, or what the phone gives when the person follows the phone.
    val current = chosen ?: Language.fromTag(context.resources.configuration.locales[0].language) ?: Language.DEFAULT
    LanguageChipContent(
        current = current,
        followsPhone = chosen == null,
        onChange = { picked ->
            viewModel.onAppLanguageChange(picked)
            // The screens are rebuilt in the new language; what has been typed or chosen is kept in the view models.
            context.findHostActivity()?.recreate()
        },
        modifier = modifier,
    )
}

@Composable
fun LanguageChipContent(current: Language, followsPhone: Boolean, onChange: (Language?) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.language_chip_description)
    Box(modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, AuthMint),
            shadowElevation = 2.dp,
            modifier = Modifier.testTag("language_chip").semantics { contentDescription = "$description: ${current.nativeName}" },
        ) {
            Row(
                modifier = Modifier.clickable { open = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_language), contentDescription = null, tint = AuthEmerald, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(current.nativeName, style = MaterialTheme.typography.labelLarge, color = AuthInk)
                Spacer(Modifier.width(2.dp))
                Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = AuthInk, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.nativeName, color = if (!followsPhone && language == current) AuthEmerald else AuthInk) },
                    onClick = {
                        open = false
                        if (followsPhone || language != current) onChange(language)
                    },
                    modifier = Modifier.testTag("language_option_${language.tag}"),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_phone), color = if (followsPhone) AuthEmerald else AuthInk) },
                onClick = {
                    open = false
                    if (!followsPhone) onChange(null)
                },
                modifier = Modifier.testTag("language_option_phone"),
            )
        }
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
