package com.rudra.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape

data class ChartSlice(val label: String, val value: Long, val color: Color)

/**
 * Donut chart with a value in the hole.
 *
 * Drawn rather than pulled in as a dependency: the app needs two chart types,
 * and a charting library would be a larger surface than the code it replaces.
 * Each slice is announced to screen readers via the chart's content description,
 * so the data is never colour-only.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val description = slices.joinToString(", ") {
        "${it.label} ${(it.value * 100 / total)} percent"
    }

    Box(
        modifier = modifier.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val stroke = 28.dp.toPx()
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            var startAngle = -90f
            for (slice in slices) {
                val sweep = slice.value.toFloat() / total.toFloat() * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep - SLICE_GAP_DEGREES,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerValue, style = MaterialTheme.typography.headlineSmall)
            Text(
                centerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ChartLegend(slices: List<ChartSlice>, valueFormatter: (Long) -> String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        for (slice in slices) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(slice.color))
                Spacer(Modifier.width(10.dp))
                Text(slice.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(valueFormatter(slice.value), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

data class BarEntry(val label: String, val value: Long)

/**
 * Simple vertical bar chart for daily or monthly series.
 *
 * Bars are drawn as views rather than on a canvas so each one carries its own
 * content description and stays reachable by touch exploration.
 */
@Composable
fun BarChart(
    entries: List<BarEntry>,
    valueFormatter: (Long) -> String,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (entries.isEmpty()) return
    val max = entries.maxOf { it.value }.coerceAtLeast(1L)

    Row(
        modifier = modifier.fillMaxWidth().height(140.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (entry in entries) {
            val fraction = entry.value.toFloat() / max.toFloat()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "${entry.label}: ${valueFormatter(entry.value)}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        // A zero-value day still gets a hairline so the axis reads
                        // as a continuous series rather than a gap.
                        .height((MAX_BAR_HEIGHT_DP * fraction).dp.coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (entry.value == 0L) barColor.copy(alpha = 0.2f) else barColor),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val SLICE_GAP_DEGREES = 2f
private const val MAX_BAR_HEIGHT_DP = 100
