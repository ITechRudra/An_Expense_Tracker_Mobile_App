package com.rudra.expensetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rudra.expensetracker.data.local.CategoryEntity

/**
 * Wrapping grid of category chips.
 *
 * A suggested category is marked but never pre-applied without being visible:
 * the user can always see what was guessed and change it in one tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<CategoryEntity>,
    selectedId: String?,
    suggestedId: String? = null,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        for (category in categories) {
            val isSuggested = category.id == suggestedId && selectedId == null
            FilterChip(
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
                label = { androidx.compose.material3.Text(category.name) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = if (isSuggested) {
                        Color(category.colorArgb).copy(alpha = 0.14f)
                    } else {
                        Color.Transparent
                    },
                ),
            )
        }
    }
}
