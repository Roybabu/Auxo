package com.auxo.pos.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Crystal liquid glass: a thin, almost clear panel floating over the page.
 *
 * What is real and what is not. The surface is a very faint white-and-mint tint (about 6-20% white), so the page
 * behind it stays clearly visible. Compose has no backdrop blur: Modifier.blur() only blurs a view's OWN content,
 * never what lies behind it, so this does not pretend to blur the background. The glass look is drawn instead, with
 * gradients: a bright outer edge, a mint inner edge, a highlight along the top edge, brighter corners, a soft
 * specular glow, and a faint shadow underneath. It costs one cached draw pass and works on every Android version
 * the app supports (minSdk 26).
 */

private val GlassWhite = Color.White
private val GlassMint = Color(0xFFB9EBD3)
private val GlassEmerald = Color(0xFF0B7A55)

/**
 * Draws [shape] as a crystal-glass panel. Put it on a node that has no background of its own; whatever is placed
 * inside is drawn on top, sharp and unaffected. [selected] only strengthens the emerald edge and the highlights;
 * the glass stays clear. [elevation] is the depth of the soft shadow underneath.
 */
fun Modifier.crystalGlass(shape: Shape, selected: Boolean = false, elevation: Dp = 6.dp): Modifier =
    this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = GlassEmerald.copy(alpha = 0.10f),
            spotColor = GlassEmerald.copy(alpha = 0.16f),
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val line = 1.dp.toPx()
            val outline = shape.createOutline(size, layoutDirection, this)
            val innerSize = Size(w - 2 * line, h - 2 * line)
            val innerOutline = shape.createOutline(innerSize, layoutDirection, this)
            val boost = if (selected) 1.25f else 1f

            // The clear body: faint white at the top left, a breath of mint in the middle, almost nothing at the bottom right.
            val body = Brush.linearGradient(
                colors = listOf(
                    GlassWhite.copy(alpha = 0.20f),
                    GlassMint.copy(alpha = 0.10f),
                    GlassWhite.copy(alpha = 0.06f),
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            // A soft glow where the light lands, top left.
            val glow = Brush.radialGradient(
                colors = listOf(GlassWhite.copy(alpha = 0.30f * boost), Color.Transparent),
                center = Offset(w * 0.18f, h * 0.06f),
                radius = w * 0.62f,
            )
            // A faint curved reflection across the upper part of the glass.
            val sheen = Brush.verticalGradient(
                colors = listOf(GlassWhite.copy(alpha = 0.16f * boost), Color.Transparent),
                startY = 0f,
                endY = h * 0.38f,
            )
            // Outer edge: bright white, a little stronger on the lit (top-left) and shadowed (bottom-right) corners.
            val outerEdge = Brush.linearGradient(
                colors = listOf(GlassWhite.copy(alpha = 0.95f), GlassWhite.copy(alpha = 0.34f), GlassWhite.copy(alpha = 0.70f)),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            // Inner edge: translucent mint, emerald when selected.
            val innerColor = if (selected) GlassEmerald.copy(alpha = 0.55f) else GlassMint.copy(alpha = 0.60f)
            val innerEdge = Brush.verticalGradient(listOf(innerColor, innerColor.copy(alpha = innerColor.alpha * 0.45f)))
            // A highlight that runs along the top edge and fades away down the sides.
            val topEdge = Brush.verticalGradient(
                colors = listOf(GlassWhite.copy(alpha = 0.95f), Color.Transparent),
                startY = 0f,
                endY = h * 0.45f,
            )
            val cornerRadius = minOf(w, h) * 0.34f
            val cornerLit = Brush.radialGradient(
                colors = listOf(GlassWhite.copy(alpha = 1f), Color.Transparent),
                center = Offset(0f, 0f),
                radius = cornerRadius,
            )
            val cornerFar = Brush.radialGradient(
                colors = listOf(GlassWhite.copy(alpha = 0.80f), Color.Transparent),
                center = Offset(w, h),
                radius = cornerRadius,
            )

            onDrawWithContent {
                drawOutline(outline, brush = body)
                drawOutline(outline, brush = glow)
                drawOutline(outline, brush = sheen)
                drawContent()
                drawOutline(outline, brush = outerEdge, style = Stroke(width = line))
                inset(line) { drawOutline(innerOutline, brush = innerEdge, style = Stroke(width = 0.75.dp.toPx())) }
                drawOutline(outline, brush = topEdge, style = Stroke(width = line * 1.4f))
                drawOutline(outline, brush = cornerLit, style = Stroke(width = line * 1.6f))
                drawOutline(outline, brush = cornerFar, style = Stroke(width = line * 1.4f))
            }
        }

/**
 * Softens the edges of a picture so a product shot with a pale backdrop melts into the glass and the page instead
 * of showing as a rectangle. The picture itself is not touched: only its outermost [feather] is faded out, so the
 * product stays sharp and fully visible.
 */
fun Modifier.featheredEdges(feather: Dp = 14.dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val f = feather.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
            val across = Brush.horizontalGradient(
                0f to Color.Transparent, f / size.width to Color.Black, 1f - f / size.width to Color.Black, 1f to Color.Transparent,
            )
            val down = Brush.verticalGradient(
                0f to Color.Transparent, f / size.height to Color.Black, 1f - f / size.height to Color.Black, 1f to Color.Transparent,
            )
            onDrawWithContent {
                drawContent()
                drawRect(across, blendMode = BlendMode.DstIn)
                drawRect(down, blendMode = BlendMode.DstIn)
            }
        }
