package com.auxo.pos.ui.screens.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auxo.pos.R
import com.auxo.pos.ui.components.CircleNextButton
import com.auxo.pos.ui.theme.AuthCard
import com.auxo.pos.ui.theme.AuthDotIdle
import com.auxo.pos.ui.theme.AuthEmerald
import com.auxo.pos.ui.theme.AuthInk
import com.auxo.pos.ui.theme.AuthInkMuted
import com.auxo.pos.ui.theme.AuthMint
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which of the three sprout pictures (0 small, 1 medium, 2 large) belongs to [step] of [stepCount]. */
internal fun sproutStage(step: Int, stepCount: Int): Int =
    if (stepCount <= 1) 2 else (step.coerceIn(0, stepCount - 1) * 2f / (stepCount - 1)).roundToInt().coerceIn(0, 2)

private val Sprouts = listOf(R.drawable.onb_sprout_1, R.drawable.onb_sprout_2, R.drawable.onb_sprout_3)

/** Height of each sprout picture, in proportion to the files (146, 248 and 316 pixels tall) so none is stretched. */
private val SproutHeights = listOf(22.dp, 37.dp, 47.dp)

private val TrackHeight = 82.dp
private val LineY = 68.dp
private val SideInset = 26.dp
private val SproutBox = 84.dp

/**
 * The illustrated progress card: step badge and counter on top, a sprout that grows as the dots fill and stays
 * above the active one, then what this step is about and the round Next button. Everything moves with [step].
 */
@Composable
fun OnboardingProgressCard(
    step: Int,
    stepCount: Int,
    title: String,
    counter: String,
    heading: String,
    body: String,
    nextDescription: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position by animateFloatAsState(
        targetValue = step.toFloat(),
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "onboarding_card_position",
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AuthCard,
        shadowElevation = 6.dp,
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("onboarding_progress"),
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    // A pale-mint glow low on the right, like the reference.
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(AuthMint.copy(alpha = 0.75f), Color.Transparent),
                            center = Offset(size.width * 0.72f, size.height * 0.55f),
                            radius = size.width * 0.55f,
                        ),
                    )
                }
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(AuthMint, CircleShape).testTag("onboarding_step_badge"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (step + 1).toString(),
                        style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = AuthEmerald),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = AuthInk),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    counter,
                    style = TextStyle(fontSize = 14.sp, color = AuthInkMuted),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("onboarding_step_counter"),
                )
            }
            Spacer(Modifier.height(4.dp))
            ProgressTrack(step = step, stepCount = stepCount, position = position, counter = counter)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        heading,
                        style = TextStyle(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, color = AuthInk),
                        modifier = Modifier.testTag("onboarding_step_heading"),
                    )
                    Text(body, style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = AuthInkMuted))
                }
                Spacer(Modifier.width(12.dp))
                CircleNextButton(onClick = onNext, contentDescription = nextDescription, modifier = Modifier.testTag("onboarding_next"))
            }
        }
    }
}

/** The line, the dots, the drifting leaves and the sprout, in a fixed-height area so nothing jumps between steps. */
@Composable
private fun ProgressTrack(step: Int, stepCount: Int, position: Float, counter: String) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .semantics(mergeDescendants = true) {
                contentDescription = counter
                progressBarRangeInfo = ProgressBarRangeInfo((step + 1).toFloat(), 1f..stepCount.toFloat(), (stepCount - 2).coerceAtLeast(0))
            },
    ) {
        val span = maxWidth - SideInset * 2
        val gap: Dp = if (stepCount > 1) span / (stepCount - 1) else 0.dp
        // Decorative leaves in the upper part only, so they never sit on the dots or the text.
        Image(
            painter = painterResource(R.drawable.onb_float_leaves),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(58.dp).alpha(0.55f).testTag("onboarding_leaves"),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = LineY.toPx()
            val x0 = SideInset.toPx()
            val stride = gap.toPx()
            val line = 3.dp.toPx()
            drawLine(AuthDotIdle, Offset(x0, y), Offset(x0 + stride * (stepCount - 1), y), line, StrokeCap.Round)
            drawLine(AuthEmerald, Offset(x0, y), Offset(x0 + stride * position, y), line, StrokeCap.Round)
            for (i in 0 until stepCount) {
                val c = Offset(x0 + stride * i, y)
                val reached = i <= position + 0.01f
                drawCircle(if (reached) AuthEmerald else AuthDotIdle, radius = 10.dp.toPx(), center = c)
                val active = (1f - abs(i - position)).coerceIn(0f, 1f)
                if (active > 0f) {
                    drawCircle(AuthEmerald, radius = 13.dp.toPx() * active + 10.dp.toPx() * (1f - active), center = c)
                    drawCircle(Color.White, radius = 9.dp.toPx() * active, center = c)
                    drawCircle(AuthEmerald, radius = 4.dp.toPx() * active, center = c)
                }
            }
        }
        // The sprout: bottom-aligned just above the active dot, following it as the position animates.
        val stage = sproutStage(step, stepCount)
        Box(
            modifier = Modifier
                .offset(x = SideInset + gap * position - SproutBox / 2)
                .width(SproutBox)
                .height(LineY - 17.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Crossfade(targetState = stage, animationSpec = tween(350), label = "onboarding_sprout") { s ->
                Image(
                    painter = painterResource(Sprouts[s]),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(SproutHeights[s]).testTag("onboarding_sprout_$s"),
                )
            }
        }
    }
}
