package com.auxo.pos.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GlassDeep = Color(0xFF0B7A55)
private val GlassLit = Color(0xFF1FB57F)
private val GlassMist = Color(0xFFBDF5D8)

/**
 * A liquid-glass button: an emerald body you can see light through, a bright curved highlight along the top like a drop of
 * water, a glow gathering at the bottom edge, and a thin white rim that is brightest at the lit corner. Used for the main
 * actions on the sign-in, set-up, recovery and lock screens. The label should be white; [lit] draws it switched on even
 * while it cannot be pressed (a busy spinner, say).
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lit: Boolean = enabled,
    shape: Shape = RoundedCornerShape(32.dp),
    height: Dp = 52.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 20.dp),
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = if (lit) Color.White else GlassDeep.copy(alpha = 0.55f),
        ),
        modifier = modifier.fillMaxWidth().height(height).liquidGlass(shape, lit),
        content = content,
    )
}

/** Draws [shape] as liquid glass; see [GlassButton]. A switched-off ([lit] false) button is a pale, clear pane. */
fun Modifier.liquidGlass(shape: Shape, lit: Boolean = true): Modifier =
    this
        .shadow(
            elevation = if (lit) 9.dp else 2.dp,
            shape = shape,
            clip = false,
            ambientColor = GlassDeep.copy(alpha = 0.25f),
            spotColor = GlassDeep.copy(alpha = if (lit) 0.45f else 0.15f),
        )
        .drawWithCache {
            val w = size.width
            val h = size.height
            val line = 1.2.dp.toPx()
            val outline = shape.createOutline(size, layoutDirection, this)
            val body = if (lit) {
                Brush.verticalGradient(listOf(GlassLit.copy(alpha = 0.93f), GlassDeep.copy(alpha = 0.88f)))
            } else {
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), GlassMist.copy(alpha = 0.40f)))
            }
            // The glow gathering along the bottom edge, where light collects in a drop.
            val bottomGlow = Brush.radialGradient(
                colors = listOf(GlassMist.copy(alpha = if (lit) 0.55f else 0.35f), Color.Transparent),
                center = Offset(w * 0.5f, h * 1.05f),
                radius = w * 0.55f,
            )
            // The curved highlight across the top half.
            val topShine = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = if (lit) 0.55f else 0.70f), Color.White.copy(alpha = 0.06f)),
                startY = 0f,
                endY = h * 0.55f,
            )
            val rim = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.65f)),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            val inset = 3.dp.toPx()
            val shineSize = Size(w - 2 * inset, h * 0.48f)
            val shineRadius = CornerRadius(h * 0.5f, h * 0.5f)
            onDrawBehind {
                drawOutline(outline, brush = body)
                drawOutline(outline, brush = bottomGlow)
                // A small bright glint at the top left, as if the glass were wet.
                drawRoundRect(
                    brush = topShine,
                    topLeft = Offset(inset, inset),
                    size = shineSize,
                    cornerRadius = shineRadius,
                )
                drawOutline(outline, brush = rim, style = Stroke(width = line))
            }
        }
