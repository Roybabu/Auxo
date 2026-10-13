package com.auxo.pos.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.ui.theme.AuthDotIdle
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthMint
import com.auxo.pos.ui.theme.AuthMintLight

/** The white-to-mint backdrop of the splash, onboarding, sign-in and setup screens. */
@Composable
fun AuthGradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White, AuthMintLight, AuthMint))),
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

/** Page dots: the current one is a long green pill, the others small grey dots. */
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
