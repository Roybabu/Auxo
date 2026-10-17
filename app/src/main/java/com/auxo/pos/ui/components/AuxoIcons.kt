package com.auxo.pos.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auxo.pos.R

/*
 * Auxo's icon system: soft-render emerald icons.
 *
 * Every glyph (the single-colour vector files in res/drawable/ic_*.xml) can be drawn in three ways through ONE
 * component, so screens never repeat gradient or shadow code:
 *
 *   AuxoIcon(R.drawable.ic_add, "Add")                       a glyph with an emerald gradient on a mint tile
 *   AuxoIcon(..., style = AuxoIconStyle.Flat)                the gradient glyph alone, for tight spots
 *   AuxoIcon(..., selected = true)                           a filled emerald tile with a white glyph
 *   AuxoArtwork(R.drawable.ic_cat_grocery, "Grocery")        the multi-colour category artwork on a mint disc
 *
 * The light always comes from the top left: bright at the top, darker at the bottom, with a faint sheen across the
 * upper half of a tile and a soft shadow under it. Everything is drawn from vectors, so it stays sharp at any
 * density, and a glyph drawn without a tile (or a file that cannot take a gradient) simply falls back to the plain
 * tinted icon.
 */

/** The emerald and mint colours every icon is drawn with. */
object AuxoIconColors {
    val Deep = Color(0xFF064E3B)
    val Mid = Color(0xFF0B7A55)
    val Bright = Color(0xFF10A56F)
    val Soft = Color(0xFF34D399)
    val Mint = Color(0xFFD9F3E7)
    val MintLight = Color(0xFFF0FBF6)
    val MintEdge = Color(0xFFBFE4CD)
    val DangerDeep = Color(0xFF991B1B)
    val DangerBright = Color(0xFFEF4444)
    val DangerTile = Color(0xFFFEE2E2)
    val DangerTileLight = Color(0xFFFFF1F1)
    val DangerEdge = Color(0xFFFECACA)
}

/** The standard sizes: the whole tile, the glyph inside it, and how round the corners are. */
enum class AuxoIconSize(val tile: Dp, val glyph: Dp, val corner: Dp) {
    /** Inside a stat tile or a chip row. */
    Mini(28.dp, 16.dp, 9.dp),

    /** The bottom navigation, which has a 32 dp indicator. */
    Nav(30.dp, 18.dp, 10.dp),

    /** Toolbar and action buttons. */
    Compact(36.dp, 20.dp, 11.dp),

    /** List rows and settings cards. */
    Medium(44.dp, 24.dp, 14.dp),

    /** Empty states and picture placeholders. */
    Large(56.dp, 28.dp, 17.dp),

    /** The largest placeholders. */
    Hero(72.dp, 36.dp, 22.dp),
}

enum class AuxoIconStyle {
    /** The glyph on a mint tile. */
    Tile,

    /** The gradient glyph alone. */
    Flat,
}

/** Which colours an icon uses: the brand emerald, or red for things that delete or warn. */
enum class AuxoIconTone {
    Brand,
    Danger,
    ;

    companion object {
        /** Deleting is always drawn in red, so every delete button reads the same everywhere. */
        fun forIcon(@DrawableRes iconRes: Int): AuxoIconTone = if (iconRes == R.drawable.ic_delete) Danger else Brand
    }
}

private class IconPalette(
    val tileTop: Color,
    val tileBottom: Color,
    val edge: Color,
    val glyphTop: Color,
    val glyphBottom: Color,
    val shadow: Color,
)

@Composable
private fun palette(tone: AuxoIconTone, selected: Boolean, accent: Color?): IconPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when {
        selected -> IconPalette(
            tileTop = AuxoIconColors.Bright, tileBottom = AuxoIconColors.Deep, edge = AuxoIconColors.Mid,
            glyphTop = Color.White, glyphBottom = AuxoIconColors.MintLight, shadow = AuxoIconColors.Deep,
        )
        accent != null -> IconPalette(
            tileTop = accent.copy(alpha = if (dark) 0.28f else 0.10f), tileBottom = accent.copy(alpha = if (dark) 0.18f else 0.20f),
            edge = accent.copy(alpha = 0.28f),
            glyphTop = accent, glyphBottom = accent.darken(0.25f), shadow = accent,
        )
        tone == AuxoIconTone.Danger -> if (dark) {
            IconPalette(Color(0xFF5A1D1D), Color(0xFF3F1313), Color(0xFF7F1D1D), Color(0xFFFCA5A5), Color(0xFFF87171), AuxoIconColors.DangerDeep)
        } else {
            IconPalette(
                AuxoIconColors.DangerTileLight, AuxoIconColors.DangerTile, AuxoIconColors.DangerEdge,
                AuxoIconColors.DangerBright, AuxoIconColors.DangerDeep, AuxoIconColors.DangerDeep,
            )
        }
        dark -> IconPalette(
            Color(0xFF14503E), Color(0xFF0B3B2D), Color(0xFF1B6B53),
            Color(0xFFA7F3D0), Color(0xFF34D399), AuxoIconColors.Deep,
        )
        else -> IconPalette(
            AuxoIconColors.MintLight, AuxoIconColors.Mint, AuxoIconColors.MintEdge,
            AuxoIconColors.Bright, AuxoIconColors.Deep, AuxoIconColors.Deep,
        )
    }
}

private fun Color.darken(fraction: Float): Color = Color(red * (1 - fraction), green * (1 - fraction), blue * (1 - fraction), alpha)

/**
 * A glyph with the Auxo soft-render look. [accent] swaps the emerald for another colour (a warning amber, say) while
 * keeping the same lighting. Pass a [contentDescription] only when the icon carries meaning on its own.
 */
@Composable
fun AuxoIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: AuxoIconSize = AuxoIconSize.Medium,
    style: AuxoIconStyle = AuxoIconStyle.Tile,
    selected: Boolean = false,
    tone: AuxoIconTone = AuxoIconTone.forIcon(iconRes),
    accent: Color? = null,
) {
    val colors = palette(tone, selected, accent)
    // A disabled button hands down a faded content colour; the icon fades with it.
    val fade = LocalContentColor.current.alpha.coerceIn(0.3f, 1f)
    if (style == AuxoIconStyle.Flat) {
        Glyph(iconRes, contentDescription, colors, modifier.size(size.glyph).graphicsLayer { alpha = fade })
        return
    }
    val shape = remember(size) { RoundedCornerShape(size.corner) }
    val tileBrush = remember(colors.tileTop, colors.tileBottom) { Brush.linearGradient(listOf(colors.tileTop, colors.tileBottom)) }
    Box(
        modifier = modifier
            .size(size.tile)
            .graphicsLayer { alpha = fade }
            .shadow(
                elevation = if (selected) 3.dp else 2.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.shadow.copy(alpha = 0.25f),
                spotColor = colors.shadow.copy(alpha = 0.35f),
            )
            .background(tileBrush, shape)
            .border(BorderStroke(0.75.dp, colors.edge), shape)
            .drawWithContent {
                drawContent()
                // A faint sheen across the upper half, as if lit from the top.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                        endY = this.size.height * 0.55f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(iconRes, contentDescription, colors, Modifier.size(size.glyph))
    }
}

/** The glyph itself: black in the file, then recoloured with a top-to-bottom gradient that keeps its shape. */
@Composable
private fun Glyph(@DrawableRes iconRes: Int, contentDescription: String?, colors: IconPalette, modifier: Modifier) {
    val brush = remember(colors.glyphTop, colors.glyphBottom) { Brush.verticalGradient(listOf(colors.glyphTop, colors.glyphBottom)) }
    Image(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(Color.Black),
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.SrcIn)
            },
    )
}

/**
 * The multi-colour category artwork (res/drawable/ic_cat_*.xml) on a soft mint disc. The artwork already carries
 * its own gradients, highlights and shadow, so it is drawn as it is, with no recolouring.
 */
@Composable
fun AuxoArtwork(
    @DrawableRes artRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    disc: Boolean = true,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val discModifier = if (disc) {
        Modifier.background(
            Brush.verticalGradient(
                if (dark) listOf(Color(0xFF14503E), Color(0xFF0B3B2D)) else listOf(AuxoIconColors.MintLight, AuxoIconColors.Mint),
            ),
            CircleShape,
        )
    } else {
        Modifier
    }
    Box(modifier = modifier.size(size).then(discModifier), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(artRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(size * if (disc) 0.76f else 1f),
        )
    }
}
