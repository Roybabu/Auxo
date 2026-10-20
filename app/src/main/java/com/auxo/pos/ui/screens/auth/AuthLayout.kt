package com.auxo.pos.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.auxo.pos.ui.components.crystalGlass
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
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
    /** Draws the form card as mint liquid glass instead of the plain white card. */
    glass: Boolean = false,
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
                    // This column already scrolls, so the card stays a plain one here.
                    FormCard(glass = false) { form() }
                    if (footer != null) {
                        Spacer(Modifier.height(Spacing.md))
                        Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) { footer() }
                    }
                }
            }
        } else if (glass && !dark) {
            // The logo and the glass stay put; only the form's contents scroll inside the glass.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    // The extra bottom padding shortens the glass from its lower edge.
                    .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xl, bottom = Spacing.xl + 128.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (topEnd != null) Spacer(Modifier.height(40.dp))
                BrandLogo(width = logoWidth, modifier = Modifier.padding(bottom = Spacing.xl))
                FormCard(glass = true, modifier = Modifier.weight(1f, fill = false)) { form() }
                if (footer != null) {
                    Spacer(Modifier.height(Spacing.md))
                    Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) { footer() }
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
                FormCard(glass) { form() }
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
private fun FormCard(glass: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (glass && MaterialTheme.colorScheme.background.luminance() >= 0.5f) {
        // Mint-tinted liquid glass: the tint sits under the glass layers so the highlights and rim stay bright.
        // The contents scroll inside it when the card is given a bounded height.
        val shape = RoundedCornerShape(44.dp)
        Box(
            modifier = modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .background(Color(0xFFB9EBD3).copy(alpha = 0.25f), shape)
                .crystalGlass(shape)
                // Scrolled text is cut at the glass's curved edge instead of spilling over it.
                .clip(shape),
        ) {
            Column(
                modifier = Modifier
                    .fadeVerticalEdges(20.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) { content() }
        }
        return
    }
    Surface(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) MaterialTheme.colorScheme.surface else AuthGreenCard,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** Fades whatever is drawn to clear over the top and bottom [edge], so scrolled content melts away instead of being cut. */
private fun Modifier.fadeVerticalEdges(edge: Dp): Modifier =
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fade = (edge.toPx() / size.height).coerceAtMost(0.5f)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    fade to Color.Black,
                    1f - fade to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
