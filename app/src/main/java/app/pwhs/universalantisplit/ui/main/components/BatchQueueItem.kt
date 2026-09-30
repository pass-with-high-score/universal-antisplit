package app.pwhs.universalantisplit.ui.main.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.domain.InstalledAppInfo
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.ui.main.BatchItemStatus
import app.pwhs.universalantisplit.ui.main.BatchItemUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BatchQueueItem(
    item: BatchItemUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCurrent = item.status == BatchItemStatus.PROCESSING

    val containerColor by animateColorAsState(
        targetValue = when (item.status) {
            BatchItemStatus.PROCESSING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            BatchItemStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceContainer
            BatchItemStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            BatchItemStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "item_container_color"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.M, vertical = Spacing.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconThumbnail(app = item.app)

            Spacer(Modifier.width(Spacing.M))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
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

                if (item.statusText.isNotBlank()) {
                    Text(
                        text = item.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (item.status) {
                            BatchItemStatus.FAILED -> MaterialTheme.colorScheme.error
                            BatchItemStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(Spacing.S))

            StatusTrailingIndicator(status = item.status)
        }
    }
}

@Composable
private fun StatusTrailingIndicator(status: BatchItemStatus) {
    when (status) {
        BatchItemStatus.PENDING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.batch_status_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        BatchItemStatus.PROCESSING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        BatchItemStatus.COMPLETED -> {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        BatchItemStatus.FAILED -> {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AppIconThumbnail(
    app: InstalledAppInfo,
    modifier: Modifier = Modifier,
    sizeDp: Int = 40
) {
    val context = LocalContext.current
    val iconBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable: Drawable = context.packageManager.getApplicationIcon(app.packageName)
                drawable.toBitmap(width = 96, height = 96)
            }.getOrNull()
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!.asImageBitmap(),
            contentDescription = app.appName,
            modifier = modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size((sizeDp * 0.65).dp)
            )
        }
    }
}
