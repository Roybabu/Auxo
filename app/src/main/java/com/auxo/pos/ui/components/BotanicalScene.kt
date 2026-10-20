package com.auxo.pos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.auxo.pos.R
import com.auxo.pos.ui.screens.auth.ART_ASPECT
import com.auxo.pos.ui.screens.auth.FORM_TOP
import com.auxo.pos.ui.screens.auth.LEAVES_ASPECT
import com.auxo.pos.ui.screens.auth.MARK_ASPECT
import com.auxo.pos.ui.screens.auth.MAX_LOGIN_WIDTH
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthMint
import com.auxo.pos.ui.theme.AuthMintLight
import com.auxo.pos.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * The scene behind the lock screen and the "Welcome back" screen: the botanical picture, whole, with the curved sheet
 * and leaves running to the foot of the screen, the Auxo mark and tagline on its dark top, and [content] written
 * straight onto the sheet (scrolling when the screen is short or the keyboard is up).
 *
 * The picture is 941 x 1672 and most phones are taller than that, so the spare height is taken up by stretching one
 * band of the sheet that is plain cream (rows [BAND_FROM]..[BAND_TO] of the file). The saplings above it and the
 * leaves below it are drawn at their true size and shape.
 */
@Composable
fun BotanicalScene(contentTag: String, content: @Composable ColumnScope.() -> Unit) {
    val picture = ImageBitmap.imageResource(R.drawable.auth_lock_bg)
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.White).testTag("auth_screen")) {
        val width = minOf(maxWidth, MAX_LOGIN_WIDTH, maxHeight / ART_ASPECT)
        val artHeight = width * ART_ASPECT
        val pageHeight = maxHeight
        Box(modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.width(width).height(pageHeight).align(Alignment.TopCenter)) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawStretchedPicture(picture) }
                SceneBrand(modifier = Modifier.fillMaxWidth().height(artHeight * FORM_TOP))
                // The sheet's clear area: from below the curve to the bottom of the screen.
                Box(modifier = Modifier.fillMaxSize().padding(top = artHeight * FORM_TOP)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
                            .testTag(contentTag),
                    ) {
                        content()
                        // Keeps the last control clear of the leaves at the foot of the picture.
                        Spacer(Modifier.height(width * LEAVES_ASPECT * 0.4f))
                    }
                }
            }
        }
    }
}

/** First and last row of the plain cream band that is stretched; the file is 1672 rows tall. */
private const val BAND_FROM = 1150
private const val BAND_TO = 1300

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStretchedPicture(picture: ImageBitmap) {
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    val scale = size.width / picture.width
    val topEnd = (BAND_FROM * scale).roundToInt()
    val bottomStart = (h - (picture.height - BAND_TO) * scale).roundToInt().coerceAtLeast(topEnd)
    val quality = FilterQuality.High
    // Top: the sky, saplings and the curve of the sheet.
    drawImage(picture, IntOffset(0, 0), IntSize(picture.width, BAND_FROM), IntOffset(0, 0), IntSize(w, topEnd), filterQuality = quality)
    // Middle: the cream band, as tall as the screen needs.
    drawImage(picture, IntOffset(0, BAND_FROM), IntSize(picture.width, BAND_TO - BAND_FROM), IntOffset(0, topEnd), IntSize(w, bottomStart - topEnd), filterQuality = quality)
    // Bottom: the leaves.
    drawImage(picture, IntOffset(0, BAND_TO), IntSize(picture.width, picture.height - BAND_TO), IntOffset(0, bottomStart), IntSize(w, h - bottomStart), filterQuality = quality)
}

/** The Auxo mark, name and tagline over the dark part of the picture. */
@Composable
internal fun SceneBrand(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.statusBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.auxo_mark),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.width(84.dp).aspectRatio(MARK_ASPECT),
        )
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

/** A pale-mint pill with the fingerprint tile at the left and an arrow at the right. */
@Composable
internal fun SceneFingerprintButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(32.dp)
    OutlinedButton(
        onClick = onClick,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AuthMintLight),
        border = BorderStroke(1.dp, AuthMint),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        AuxoIcon(R.drawable.ic_fingerprint, contentDescription = null, size = AuxoIconSize.Compact)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = AuthEmerald,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        AuxoIcon(R.drawable.ic_arrow_forward, contentDescription = null, size = AuxoIconSize.Compact, style = AuxoIconStyle.Flat)
    }
}

