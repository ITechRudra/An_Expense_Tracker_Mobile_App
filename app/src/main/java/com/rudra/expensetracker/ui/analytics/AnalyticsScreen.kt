package com.rudra.expensetracker.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.expensetracker.core.money.Money
import com.rudra.expensetracker.ui.components.BarChart
import com.rudra.expensetracker.ui.components.BarEntry
import com.rudra.expensetracker.ui.components.ChartLegend
import com.rudra.expensetracker.ui.components.ChartSlice
import com.rudra.expensetracker.ui.components.DonutChart
import com.rudra.expensetracker.ui.components.EmptyState
import com.rudra.expensetracker.ui.components.LoadingState
import com.rudra.expensetracker.ui.components.SectionHeader
import com.rudra.expensetracker.ui.components.StatTile
import com.rudra.expensetracker.ui.theme.expenseColor
import com.rudra.expensetracker.ui.theme.incomeColor
import com.rudra.expensetracker.util.formatCompact
import com.rudra.expensetracker.util.formatRupees
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Analytics") }) }) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(Modifier.padding(padding)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                    AnalyticsPeriod.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = state.period == option,
                            onClick = { viewModel.onPeriodChange(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, AnalyticsPeriod.entries.size),
                        ) { Text(option.label) }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile("Income", state.income.formatRupees(), Modifier.weight(1f), incomeColor())
                    StatTile("Expenses", state.expense.formatRupees(), Modifier.weight(1f), expenseColor())
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile("Net", state.net.formatRupees(), Modifier.weight(1f))
                    StatTile("Avg / day", state.averageDaily.formatRupees(), Modifier.weight(1f))
                }

                state.expenseChangePercent?.let { change ->
                    Text(
                        text = when {
                            change > 0 -> "Spending is up $change% versus the previous ${state.period.label.lowercase()}"
                            change < 0 -> "Spending is down ${-change}% versus the previous ${state.period.label.lowercase()}"
                            else -> "Spending matches the previous ${state.period.label.lowercase()}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            if (state.expense.isZero && state.income.isZero) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Analytics,
                        title = "Nothing to analyse yet",
                        message = "Once a few transactions are recorded, this screen breaks them down by category, day and merchant.",
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
                return@LazyColumn
            }

            if (state.daily.isNotEmpty()) {
                item {
                    SectionHeader("Spending by day")
                    val formatter = DateTimeFormatter.ofPattern(
                        if (state.period == AnalyticsPeriod.YEAR) "MMM" else "d",
                    )
                    // A year of daily bars is unreadable; sample down to fit.
                    val entries = state.daily
                        .let { series ->
                            val step = (series.size / MAX_BARS).coerceAtLeast(1)
                            series.filterIndexed { index, _ -> index % step == 0 }
                        }
                        .map { BarEntry(it.date.format(formatter), it.expense.minorUnits) }
                    BarChart(entries, { Money(it).formatCompact() })
                }
            }

            if (state.byCategory.isNotEmpty()) {
                item {
                    SectionHeader("By category")
                    val slices = state.byCategory.take(6).map { total ->
                        val category = total.categoryId?.let { state.categoriesById[it] }
                        ChartSlice(
                            label = category?.name ?: "Uncategorised",
                            value = total.total.minorUnits,
                            color = category?.let { Color(it.colorArgb) }
                                ?: MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    DonutChart(slices, "spent", state.expense.formatRupees())
                    ChartLegend(slices) { Money(it).formatRupees() }
                }
            }

            if (state.topMerchants.isNotEmpty()) {
                item { SectionHeader("Top merchants") }
                items(state.topMerchants.size) { index ->
                    val merchant = state.topMerchants[index]
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(merchant.merchantKey, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${merchant.total.formatRupees()} · ${merchant.count}×",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_BARS = 31
