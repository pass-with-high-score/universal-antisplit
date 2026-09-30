package app.pwhs.universalantisplit.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.ui.main.BatchItemStatus
import app.pwhs.universalantisplit.ui.main.BatchItemUiState
import app.pwhs.universalantisplit.ui.main.BatchProgressState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchSummaryDialog(
    batchState: BatchProgressState,
    onInstallItem: (File) -> Unit,
    onShareItem: (File) -> Unit,
    onViewHistory: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    if (!batchState.isCompleted) return

    val isAllSuccess = batchState.failedCount == 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = Spacing.L, vertical = Spacing.L)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                Icon(
                    imageVector = if (isAllSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (isAllSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )

                Column {
                    Text(
                        text = stringResource(R.string.batch_summary_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isAllSuccess) {
                            stringResource(
                                R.string.batch_summary_desc_success,
                                batchState.completedCount,
                                batchState.totalCount
                            )
                        } else {
                            stringResource(
                                R.string.batch_summary_desc_partial,
                                batchState.completedCount,
                                batchState.totalCount,
                                batchState.failedCount
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Spacing.XS))

            // Completed List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                items(batchState.items, key = { it.workId }) { item ->
                    SummaryItemRow(
                        item = item,
                        onInstall = { item.outputFile?.let(onInstallItem) },
                        onShare = { item.outputFile?.let(onShareItem) }
                    )
                }
            }

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onViewHistory()
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Spacing.S))
                    Text(stringResource(R.string.batch_action_history))
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(stringResource(R.string.batch_action_done))
                }
            }
        }
    }
}

@Composable
private fun SummaryItemRow(
    item: BatchItemUiState,
    onInstall: () -> Unit,
    onShare: () -> Unit
) {
    val isCompleted = item.status == BatchItemStatus.COMPLETED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconThumbnail(app = item.app, sizeDp = 42)

            Spacer(Modifier.width(Spacing.M))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isCompleted) {
                    item.outputFile?.let { file ->
                        val sizeMb = file.length() / (1024.0 * 1024.0)
                        Text(
                            text = String.format("%.2f MB", sizeMb),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.batch_error_prefix, item.errorMessage ?: "Failed"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isCompleted && item.outputFile != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
                ) {
                    FilledTonalButton(
                        onClick = onInstall,
                        shape = MaterialTheme.shapes.small,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.batch_action_install),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.batch_action_share),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
