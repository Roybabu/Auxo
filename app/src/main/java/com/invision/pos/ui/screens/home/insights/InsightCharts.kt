package com.invision.pos.ui.screens.home.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invision.pos.domain.insights.ComparisonBucket
import com.invision.pos.domain.insights.CompactMoney
import com.invision.pos.domain.insights.SeriesPoint
import com.invision.pos.domain.model.Money
import com.invision.pos.ui.theme.LocalDarkTheme
import com.invision.pos.ui.theme.Spacing
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Series colours. Each has at least 3:1 contrast on the card behind it, in both the light
 * (white) and dark (black) theme, and every chart also writes the values out (in labels,
 * legends or a text description), so colour is never the only cue.
 */
object ChartPalette {
    val Green: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF4FB39E) else Color(0xFF2F6E61)
    val Amber: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFFBBF24) else Color(0xFFB45309)
    val Blue: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val Slate: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFF94A3B8) else Color(0xFF64748B)
    val Purple: Color @Composable get() = if (LocalDarkTheme.current) Color(0xFFC084FC) else Color(0xFF7E22CE)

    /** In order, for charts that need several. */
    val Series: List<Color> @Composable get() = listOf(Green, Amber, Blue, Slate, Purple)
}

private val ChartHeight = 150.dp
private val AxisWidth = 44.dp
private val PlotInset = 8.dp

/** One entry of a legend. */
data class LegendItem(val color: Color, val label: String)

@Composable
fun ChartLegend(items: List<LegendItem>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(item.color),
                )
                Text(text = item.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A plot area with value labels down the left and [xLabels] underneath. */
@Composable
private fun AxisChart(
    top: String,
    middle: String?,
    bottom: String,
    xLabels: List<String>,
    maxXLabels: Int,
    modifier: Modifier = Modifier,
    plot: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .height(ChartHeight)) {
            Column(
                modifier = Modifier
                    .width(AxisWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                AxisText(top)
                if (middle != null) AxisText(middle)
                AxisText(bottom)
            }
            Spacer(Modifier.width(Spacing.sm))
            plot(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        XLabels(labels = xLabels, maxShown = maxXLabels, modifier = Modifier.padding(start = AxisWidth + Spacing.sm))
    }
}

@Composable
private fun AxisText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** Labels spread evenly under a chart; when there are many, only every few are shown. */
@Composable
private fun XLabels(labels: List<String>, maxShown: Int, modifier: Modifier = Modifier) {
    val step = max(1, ceil(labels.size / maxShown.toFloat()).toInt())
    Row(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (index % step == 0) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/**
 * Line chart of one amount over time. The scale always includes zero, so a day of net
 * returns shows below the line instead of being hidden. [description] is what screen
 * readers say instead of the picture.
 */
@Composable
fun LineChart(
    points: List<SeriesPoint>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = ChartPalette.Green,
) {
    val values = points.map { it.amount.minorUnits }
    val maxValue = max(0L, values.maxOrNull() ?: 0L)
    val minValue = min(0L, values.minOrNull() ?: 0L)
    val range = (maxValue - minValue).coerceAtLeast(1L)
    val grid = MaterialTheme.colorScheme.outlineVariant

    AxisChart(
        top = CompactMoney.format(Money(maxValue)),
        middle = if (maxValue - minValue > 1) CompactMoney.format(Money(minValue + range / 2)) else null,
        bottom = CompactMoney.format(Money(minValue)),
        xLabels = points.map { it.label },
        maxXLabels = 7,
        modifier = modifier
            .testTag("chart_line")
            .clearAndSetSemantics { contentDescription = description },
    ) { plotModifier ->
        Canvas(modifier = plotModifier) {
            val inset = PlotInset.toPx()
            val plotHeight = size.height - 2 * inset
            fun yOf(value: Long) = inset + plotHeight * (1f - (value - minValue).toFloat() / range)
            fun xOf(index: Int) = if (points.size == 1) size.width / 2 else size.width * index / (points.size - 1)

            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = inset + plotHeight * fraction
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            if (points.size > 1) {
                val line = Path().apply {
                    moveTo(xOf(0), yOf(values[0]))
                    for (i in 1 until points.size) lineTo(xOf(i), yOf(values[i]))
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(xOf(points.lastIndex), yOf(0))
                    lineTo(xOf(0), yOf(0))
                    close()
                }
                drawPath(area, color.copy(alpha = 0.12f))
                drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (points.size <= 14) {
                points.indices.forEach { i -> drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(xOf(i), yOf(values[i]))) }
            }
        }
    }
}

/**
 * Two bars per step, side by side, for comparing two amounts such as sales and purchases.
 * Negative amounts are drawn as no bar; the legend and [description] carry the totals.
 */
@Composable
fun PairedBarChart(
    buckets: List<ComparisonBucket>,
    description: String,
    modifier: Modifier = Modifier,
    firstColor: Color = ChartPalette.Green,
    secondColor: Color = ChartPalette.Amber,
) {
    val maxValue = buckets.maxOfOrNull { max(max(it.sales.minorUnits, it.purchases.minorUnits), 0L) }?.coerceAtLeast(1L) ?: 1L
    val grid = MaterialTheme.colorScheme.outlineVariant

    AxisChart(
        top = CompactMoney.format(Money(maxValue)),
        middle = CompactMoney.format(Money(maxValue / 2)),
        bottom = CompactMoney.format(Money.ZERO),
        xLabels = buckets.map { it.label },
        maxXLabels = 7,
        modifier = modifier
            .testTag("chart_bars")
            .clearAndSetSemantics { contentDescription = description },
    ) { plotModifier ->
        Canvas(modifier = plotModifier) {
            val inset = PlotInset.toPx()
            val plotHeight = size.height - 2 * inset
            val baseline = inset + plotHeight
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = inset + plotHeight * fraction
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val slot = size.width / buckets.size
            val barWidth = min(slot * 0.32f, 18.dp.toPx())
            val gap = 1.dp.toPx()
            val minHeight = 2.dp.toPx()
            fun bar(value: Long, left: Float, color: Color) {
                if (value <= 0) return
                val height = max(plotHeight * value / maxValue, minHeight)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, baseline - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
            buckets.forEachIndexed { index, bucket ->
                val centre = slot * (index + 0.5f)
                bar(bucket.sales.minorUnits, centre - barWidth - gap, firstColor)
                bar(bucket.purchases.minorUnits, centre + gap, secondColor)
            }
        }
    }
}

/** One slice of a [DonutChart]; [valueText] is what the legend shows next to the share. */
data class DonutSlice(val label: String, val value: Long, val color: Color, val valueText: String)

/** A ring split by share, with the total in the middle and a legend that spells out every value. */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centreLabel: String,
    centreValue: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    Row(
        modifier = modifier
            .testTag("chart_donut")
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 20.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                var start = -90f
                slices.forEach { slice ->
                    val sweep = 360f * slice.value / total
                    val gap = if (slices.size > 1) 2f else 0f
                    drawArc(
                        color = slice.color,
                        startAngle = start + gap / 2,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = centreValue, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(text = centreLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(slice.color),
                    )
                    Column {
                        Text(text = slice.label, style = MaterialTheme.typography.bodySmall)
                        val share = Math.round(slice.value * 100.0 / total)
                        Text(
                            text = "${slice.valueText} · $share%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** One row of [HorizontalBarChart]. */
data class BarItem(val label: String, val value: Long, val valueText: String, val color: Color)

/** Ranked horizontal bars, each with its label and value written above it. */
@Composable
fun HorizontalBarChart(
    items: List<BarItem>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val maxValue = items.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Column(
        modifier = modifier
            .testTag("chart_hbars")
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(text = item.valueText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((item.value.toFloat() / maxValue).coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(item.color),
                    )
                }
            }
        }
    }
}

/** One part of a [StackedBar]. */
data class StackedSegment(val label: String, val value: Int, val color: Color)

/** A single bar split into parts by share, with a legend that gives each count. */
@Composable
fun StackedBar(
    segments: List<StackedSegment>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val shown = segments.filter { it.value > 0 }
    Column(
        modifier = modifier
            .testTag("chart_stacked")
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
        ) {
            shown.forEach { segment ->
                Box(
                    Modifier
                        .weight(segment.value.toFloat())
                        .fillMaxHeight()
                        .background(segment.color),
                )
            }
        }
        ChartLegend(segments.map { LegendItem(it.color, "${it.label} ${it.value}") })
    }
}
