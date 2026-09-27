package app.pwhs.universalantisplit.ui.history.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.ui.history.HistorySourceFilter
import app.pwhs.universalantisplit.ui.history.HistoryStatusFilter

@Composable
fun HistoryFilterChips(
    statusFilter: HistoryStatusFilter,
    onStatusFilterChanged: (HistoryStatusFilter) -> Unit,
    sourceFilter: HistorySourceFilter,
    onSourceFilterChanged: (HistorySourceFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        labelColor = MaterialTheme.colorScheme.onSurface,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = statusFilter == HistoryStatusFilter.ALL,
            onClick = { onStatusFilterChanged(HistoryStatusFilter.ALL) },
            label = { Text(stringResource(R.string.history_filter_all)) },
            colors = chipColors
        )

        FilterChip(
            selected = statusFilter == HistoryStatusFilter.SUCCESS,
            onClick = {
                onStatusFilterChanged(
                    if (statusFilter == HistoryStatusFilter.SUCCESS) HistoryStatusFilter.ALL else HistoryStatusFilter.SUCCESS
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text(stringResource(R.string.history_filter_success)) },
            colors = chipColors
        )

        FilterChip(
            selected = statusFilter == HistoryStatusFilter.FAILED,
            onClick = {
                onStatusFilterChanged(
                    if (statusFilter == HistoryStatusFilter.FAILED) HistoryStatusFilter.ALL else HistoryStatusFilter.FAILED
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text(stringResource(R.string.history_filter_failed)) },
            colors = chipColors
        )

        FilterChip(
            selected = sourceFilter == HistorySourceFilter.INSTALLED,
            onClick = {
                onSourceFilterChanged(
                    if (sourceFilter == HistorySourceFilter.INSTALLED) HistorySourceFilter.ALL else HistorySourceFilter.INSTALLED
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text(stringResource(R.string.history_filter_installed)) },
            colors = chipColors
        )

        FilterChip(
            selected = sourceFilter == HistorySourceFilter.CONTAINER,
            onClick = {
                onSourceFilterChanged(
                    if (sourceFilter == HistorySourceFilter.CONTAINER) HistorySourceFilter.ALL else HistorySourceFilter.CONTAINER
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text(stringResource(R.string.history_filter_container)) },
            colors = chipColors
        )
    }
}
