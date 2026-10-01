package app.pwhs.universalantisplit.ui.history.components

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GetApp
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.data.local.db.entity.ConversionHistory
import app.pwhs.universalantisplit.domain.ApkFileInfo
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.theme.UniversalAntiSplitTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemActionsSheet(
    item: ConversionHistory,
    apkInfo: ApkFileInfo?,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onOpenFolder: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Spacing.M)
        ) {
            val iconBitmap by rememberApkIcon(item.packageName, item.outputPath)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S)
            ) {
                val bitmap = iconBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
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
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.appName.ifBlank { item.packageName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.packageName.isNotBlank() && item.packageName != item.appName) {
                        Text(
                            text = item.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (item.isSuccessful) {
                val context = LocalContext.current
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.XS),
                    modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS)
                ) {
                    val version = buildString {
                        append(apkInfo?.versionName ?: item.versionName.ifBlank { "—" })
                        val code = apkInfo?.versionCode ?: 0L
                        if (code > 0) append(" ($code)")
                    }
                    InfoRow(stringResource(R.string.history_info_version), version)
                    if (item.fileSizeBytes > 0) {
                        InfoRow(
                            stringResource(R.string.history_info_size),
                            Formatter.formatShortFileSize(context, item.fileSizeBytes)
                        )
                    }
                    if (apkInfo != null && (apkInfo.minSdk > 0 || apkInfo.targetSdk > 0)) {
                        InfoRow(
                            stringResource(R.string.history_info_sdk),
                            "API ${apkInfo.minSdk} → ${apkInfo.targetSdk}"
                        )
                    }
                    if (apkInfo != null && apkInfo.abis.isNotEmpty()) {
                        InfoRow(
                            stringResource(R.string.history_info_abi),
                            apkInfo.abis.joinToString(", ")
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (item.isSuccessful) {
                ActionRow(Icons.Rounded.GetApp, stringResource(R.string.action_install_merged)) {
                    onInstall(); onDismissRequest()
                }
                ActionRow(Icons.Rounded.Share, stringResource(R.string.action_share_merged)) {
                    onShare(); onDismissRequest()
                }
                ActionRow(Icons.Rounded.FolderOpen, stringResource(R.string.history_open_folder)) {
                    onOpenFolder(); onDismissRequest()
                }
                ActionRow(Icons.Rounded.ContentCopy, stringResource(R.string.history_copy_path)) {
                    onCopyPath(); onDismissRequest()
                }
            }

            ActionRow(
                icon = Icons.Rounded.DeleteForever,
                label = stringResource(R.string.history_delete_item),
                tint = MaterialTheme.colorScheme.error
            ) {
                onDelete(); onDismissRequest()
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.L),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.L, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}

@Preview
@Composable
private fun HistoryItemActionsSheetPreview() {
    UniversalAntiSplitTheme {
        Column {
            InfoRow(stringResource(R.string.history_info_version), "1.0.983304623 (983304623)")
            InfoRow(stringResource(R.string.history_info_size), "3.7 MB")
            InfoRow(stringResource(R.string.history_info_sdk), "API 24 → 35")
            InfoRow(stringResource(R.string.history_info_abi), "arm64-v8a, armeabi-v7a")
            ActionRow(Icons.Rounded.GetApp, stringResource(R.string.action_install_merged)) {}
            ActionRow(Icons.Rounded.Share, stringResource(R.string.action_share_merged)) {}
            ActionRow(Icons.Rounded.FolderOpen, stringResource(R.string.history_open_folder)) {}
            ActionRow(Icons.Rounded.ContentCopy, stringResource(R.string.history_copy_path)) {}
            ActionRow(
                icon = Icons.Rounded.DeleteForever,
                label = stringResource(R.string.history_delete_item),
                tint = MaterialTheme.colorScheme.error
            ) {}
        }
    }
}
