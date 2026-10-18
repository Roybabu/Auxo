package com.auxo.pos.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.ui.theme.AuthGreenCard
import com.auxo.pos.ui.theme.AuthGreenPage
import com.auxo.pos.ui.theme.AuthGreenPageDeep
import com.auxo.pos.ui.theme.Spacing

/** Width from which the brand sits beside the form instead of above it (tablets, phones on their side). */
private val WIDE_LAYOUT_FROM = 720.dp

/** The approved Auxo logo (symbol, wordmark and tagline), transparent: on green it needs a light tile behind it. */
@Composable
private fun BrandLogo(width: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.auxo_logo_horizontal),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        modifier = modifier.width(width).aspectRatio(LOGO_ASPECT),
    )
}

/** Width over height of auxo_logo_horizontal.png. */
private const val LOGO_ASPECT = 1050f / 342f

/**
 * Shared frame of the Admin Setup and Login screens: the Auxo logo on green and white.
 * Narrow screens stack the brand above a centred form; wide screens put it in a green panel beside the form.
 */
@Composable
fun AuthLayout(
    modifier: Modifier = Modifier,
    /** A full-screen picture behind everything (not used in dark mode). */
    @DrawableRes backdropImage: Int? = null,
    logoWidth: Dp = 280.dp,
    /** Loose pictures (leaves) drawn behind the logo and the card; placed by the screen's width and height. */
    decoration: @Composable BoxWithConstraintsScope.() -> Unit = {},
    /** Shown under the card. */
    footer: (@Composable () -> Unit)? = null,
    /** Pinned to the top right corner of the screen (the language button). */
    topEnd: (@Composable () -> Unit)? = null,
    form: @Composable () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backdrop = if (dark) Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))
    else Brush.verticalGradient(listOf(AuthGreenPage, AuthGreenPageDeep))
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(backdrop).testTag("auth_screen")) {
        if (backdropImage != null && !dark) {
            Image(
                painter = painterResource(backdropImage),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        decoration()
        if (maxWidth >= WIDE_LAYOUT_FROM) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                        .safeDrawingPadding()
                        .padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Column(
                        modifier = Modifier.clip(RoundedCornerShape(36.dp)).background(AuthGreenCard).padding(32.dp),
                    ) { BrandLogo(width = 320.dp) }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .safeDrawingPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    FormCard { form() }
                    if (footer != null) {
                        Spacer(Modifier.height(Spacing.md))
                        Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) { footer() }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Leave room for the corner button so it never sits on the logo.
                if (topEnd != null) Spacer(Modifier.height(40.dp))
                BrandLogo(width = logoWidth, modifier = Modifier.padding(bottom = Spacing.xl))
                FormCard { form() }
                if (footer != null) {
                    Spacer(Modifier.height(Spacing.md))
                    Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) { footer() }
                }
            }
        }
        if (topEnd != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp)) { topEnd() }
        }
    }
}

/** The white rounded card the sign-in and setup forms sit on, over the mint backdrop. */
@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) MaterialTheme.colorScheme.surface else AuthGreenCard,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}
