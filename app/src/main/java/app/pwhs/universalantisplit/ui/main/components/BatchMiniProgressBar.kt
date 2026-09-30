package app.pwhs.universalantisplit.ui.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.pwhs.universalantisplit.ui.main.BatchProgressState

@Composable
fun BatchMiniProgressBar(
    batchState: BatchProgressState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = batchState.isBatchActive && !batchState.isSheetVisible && !batchState.isCompleted

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        val currentApp = batchState.currentItem?.app
        val currentIndex = (batchState.items.indexOfFirst {
            it.status == BatchItemStatus.PROCESSING
        }.takeIf { it >= 0 } ?: batchState.completedCount) + 1
        val percent = (batchState.overallProgress * 100).toInt().coerceIn(0, 100)

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.L, vertical = Spacing.S)
                .clickable(onClick = onExpand),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.M, vertical = Spacing.M),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentApp != null) {
                    AppIconThumbnail(app = currentApp, sizeDp = 36)
                    Spacer(Modifier.width(Spacing.M))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            R.string.batch_minibar_title,
                            currentIndex.coerceAtMost(batchState.totalCount),
                            batchState.totalCount,
                            currentApp?.appName ?: ""
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.batch_minibar_desc, percent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }

                Spacer(Modifier.width(Spacing.S))

                CircularProgressIndicator(
                    progress = { batchState.overallProgress },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
            }
        }
    }
}
