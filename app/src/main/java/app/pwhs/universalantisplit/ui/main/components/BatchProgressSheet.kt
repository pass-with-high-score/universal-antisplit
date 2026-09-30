package app.pwhs.universalantisplit.ui.main.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.ui.main.BatchItemStatus
import app.pwhs.universalantisplit.ui.main.BatchProgressState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchProgressSheet(
    batchState: BatchProgressState,
    onMinimize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    if (!batchState.isBatchActive || !batchState.isSheetVisible) return

    var showCancelDialog by remember { mutableStateOf(false) }

    val animatedOverallProgress by animateFloatAsState(
        targetValue = batchState.overallProgress,
        animationSpec = tween(durationMillis = 350),
        label = "overall_progress"
    )
    val overallPercent = (animatedOverallProgress * 100).toInt().coerceIn(0, 100)

    val currentProcessingIndex = batchState.items.indexOfFirst {
        it.status == BatchItemStatus.PROCESSING
    }.takeIf { it >= 0 } ?: batchState.completedCount.coerceAtMost(batchState.totalCount - 1)

    ModalBottomSheet(
        onDismissRequest = onMinimize,
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
            // Header with title, progress percent & actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(
                                R.string.batch_progress_title,
                                (currentProcessingIndex + 1).coerceAtMost(batchState.totalCount),
                                batchState.totalCount
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.batch_progress_overall_label, overallPercent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMinimize) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.batch_action_minimize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showCancelDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.batch_action_cancel),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Overall Progress bar
            LinearProgressIndicator(
                progress = { animatedOverallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            // Current Active App Hero Card
            batchState.currentItem?.let { currentItem ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.M),
                        verticalArrangement = Arrangement.spacedBy(Spacing.S)
                    ) {
                        Text(
                            text = stringResource(R.string.batch_active_title),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppIconThumbnail(app = currentItem.app, sizeDp = 44)
                            Spacer(Modifier.width(Spacing.M))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentItem.app.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentItem.app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val itemProgress by animateFloatAsState(
                            targetValue = currentItem.progress,
                            animationSpec = tween(durationMillis = 300),
                            label = "active_item_progress"
                        )
                        LinearProgressIndicator(
                            progress = { itemProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )

                        if (currentItem.statusText.isNotBlank()) {
                            Text(
                                text = currentItem.statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Queue List Title
            Text(
                text = stringResource(R.string.batch_queue_title, batchState.totalCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Scrollable Queue List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                items(batchState.items, key = { it.workId }) { item ->
                    BatchQueueItem(item = item)
                }
            }

            // Bottom Notice
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                modifier = Modifier.padding(top = Spacing.XS)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.merge_progress_please_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.batch_dialog_cancel_title)) },
            text = { Text(stringResource(R.string.batch_dialog_cancel_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancel()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.batch_dialog_cancel_confirm),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.batch_dialog_cancel_dismiss))
                }
            }
        )
    }
}
