package com.auxo.pos.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.ui.theme.AuthAmber
import com.auxo.pos.ui.theme.AuthDotIdle
import com.auxo.pos.ui.theme.AuthEmeraldBright
import com.auxo.pos.ui.theme.AuthInkMuted
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthMint
import com.auxo.pos.ui.theme.AuthMintLight

/** The white-to-mint backdrop of the splash, onboarding, sign-in and setup screens. */
@Composable
fun AuthGradientBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Color.White, AuthMintLight, AuthMint),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors)),
        content = content,
    )
}

/** A decorative picture: not read out by screen readers, never stretched. */
@Composable
fun Illustration(@DrawableRes id: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/** The round green "next" arrow at the bottom right of the onboarding pages. */
@Composable
fun CircleNextButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = AuthEmerald, contentColor = Color.White),
    ) {
        // The back arrow turned round: the app has no forward arrow of its own.
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = contentDescription, modifier = Modifier.rotate(180f))
    }
}

/** Page dots: the current one is a long green line, the others small grey dots. */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val active = index == current
            val width: Dp by animateDpAsState(if (active) 20.dp else 6.dp, label = "dot")
            Box(
                modifier = Modifier
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (active) AuthEmerald else AuthDotIdle),
            )
        }
    }
}

/** The thin progress line on the splash page: [fraction] of it is green. */
@Composable
fun AuthProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(4.dp).clip(CircleShape).background(AuthDotIdle)) {
        Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(AuthEmerald))
    }
}

/** The four-segment password strength bar. [filled] is 0 to 4; it is hidden while the password is empty. */
@Composable
fun StrengthMeter(filled: Int, label: String, modifier: Modifier = Modifier) {
    if (filled <= 0) return
    val colour = when (filled) {
        1 -> MaterialTheme.colorScheme.error
        2 -> AuthAmber
        3 -> AuthEmeraldBright
        else -> AuthEmerald
    }
    Column(modifier = modifier.testTag("strength_meter"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(if (index < filled) colour else AuthDotIdle),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = colour, modifier = Modifier.testTag("strength_label"))
    }
}

/** A row of tappable ideas (for example usernames); scrolls sideways rather than wrapping. */
@Composable
fun SuggestionRow(label: String, suggestions: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (suggestions.isEmpty()) return
    Column(modifier = modifier.testTag("suggestions"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AuthInkMuted)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            suggestions.forEachIndexed { index, text ->
                AssistChip(onClick = { onPick(text) }, label = { Text(text) }, modifier = Modifier.testTag("suggestion_$index"))
            }
        }
    }
}

/**
 * A pill-shaped, pale-mint input with a round icon at the left, used on the sign-in page. The hint sits inside the pill until
 * something is typed; an [error] replaces the line under it.
 */
@Composable
fun AuthPillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(32.dp),
        placeholder = { Text(placeholder, color = AuthInkMuted) },
        leadingIcon = {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(AuthMint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = AuthEmerald, modifier = Modifier.size(22.dp))
            }
        },
        trailingIcon = trailingIcon,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = AuthMintLight,
            focusedContainerColor = AuthMintLight,
            disabledContainerColor = AuthMintLight,
            unfocusedBorderColor = AuthMint,
            focusedBorderColor = AuthEmerald,
        ),
    )
}

/** A thin line, a short word, a thin line: the "or" between sign in and set up. */
@Composable
fun OrDivider(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = AuthDotIdle)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AuthInkMuted)
        HorizontalDivider(modifier = Modifier.weight(1f), color = AuthDotIdle)
    }
}
