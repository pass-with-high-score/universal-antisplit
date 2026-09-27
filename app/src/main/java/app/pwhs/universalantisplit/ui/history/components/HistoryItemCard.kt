package app.pwhs.universalantisplit.ui.history.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import app.pwhs.universalantisplit.data.local.db.entity.ConversionHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryItemCard(
    item: ConversionHistory,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dateFormatted = remember(item.timestamp) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }

    val iconBitmap by produceState<Bitmap?>(initialValue = null, key1 = item.packageName, key2 = item.outputPath) {
        value = withContext(Dispatchers.IO) {
            val fromInstalled = runCatching {
                if (item.packageName.isNotBlank()) {
                    val drawable: Drawable = context.packageManager.getApplicationIcon(item.packageName)
                    drawable.toBitmap(width = 96, height = 96)
                } else null
            }.getOrNull()

            if (fromInstalled != null) {
                fromInstalled
            } else {
                runCatching {
                    if (item.outputPath.isNotBlank()) {
                        val direct = File(item.outputPath)
                        val file = if (direct.exists()) direct else {
                            val localDir = context.getExternalFilesDir("merged") ?: context.filesDir
                            File(localDir, direct.name).takeIf { it.exists() }
                        }
                        if (file != null && file.exists()) {
                            val pm = context.packageManager
                            val pi = pm.getPackageArchiveInfo(file.absolutePath, 0)
                            val appInfo = pi?.applicationInfo
                            if (appInfo != null) {
                                appInfo.sourceDir = file.absolutePath
                                appInfo.publicSourceDir = file.absolutePath
                                appInfo.loadIcon(pm).toBitmap(width = 96, height = 96)
                            } else null
                        } else null
                    } else null
                }.getOrNull()
            }
        }
    }

    OutlinedCard(
        onClick = onShare,
        enabled = item.isSuccessful,
        modifier = modifier.fillMaxWidth(),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap!!.asImageBitmap(),
                        contentDescription = item.appName.ifBlank { item.packageName },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.appName.ifBlank { item.packageName },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (item.isSuccessful) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (item.isSuccessful) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (item.appName.isNotBlank() && item.packageName.isNotBlank() && item.appName != item.packageName) {
                        Text(
                            text = item.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                if (item.isSuccessful) {
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.history_share_apk),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.history_deleted_item),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val source = if (item.sourceType == "INSTALLED") {
                    stringResource(R.string.history_source_installed)
                } else {
                    stringResource(R.string.history_source_container)
                }
                HistoryChip(source)
                HistoryChip(stringResource(R.string.history_splits_count, item.splitCount))
                if (!item.isSuccessful) {
                    HistoryChip(
                        stringResource(R.string.history_status_failed),
                        isError = true
                    )
                }
            }

            if (item.isSuccessful && item.outputPath.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.history_output_path, item.outputPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!item.isSuccessful && !item.errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.history_error_message, item.errorMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = dateFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryChip(label: String, isError: Boolean = false) {
    OutlinedCard(border = CardDefaults.outlinedCardBorder()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
