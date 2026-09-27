package app.pwhs.universalantisplit.ui.main.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.theme.ExtendedColors
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.ui.components.StatusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SelectedAppCard(
    appName: String,
    packageName: String?,
    versionName: String?,
    fileSize: String?,
    splitCount: Int,
    isPairIpDetected: Boolean,
    isInstalledApp: Boolean,
    iconBitmap: Bitmap?,
    onChangeClick: () -> Unit,
    extendedColors: ExtendedColors,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val installedAppIcon by produceState<Bitmap?>(initialValue = null, key1 = packageName) {
        if (isInstalledApp && packageName != null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
                    drawable.toBitmap(width = 144, height = 144)
                }.getOrNull()
            }
        }
    }

    val displayIcon = iconBitmap ?: installedAppIcon

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.L)) {
            // Header Row: Avatar, App Name, Subtitle, Change Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Avatar
                if (displayIcon != null) {
                    Image(
                        bitmap = displayIcon.asImageBitmap(),
                        contentDescription = appName,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Android,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(Spacing.M))

                // App Title & Version/Size Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!versionName.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.version_prefix, versionName),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!fileSize.isNullOrBlank()) {
                                Text(
                                    text = "  •  ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                        if (!fileSize.isNullOrBlank()) {
                            Text(
                                text = fileSize,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.width(Spacing.S))

                FilledTonalButton(
                    onClick = onChangeClick,
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_change), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Dedicated Package Name pill (full width, monospace, click to copy)
            if (!packageName.isNullOrBlank()) {
                val copiedMsg = stringResource(R.string.msg_copied_package, packageName)
                Spacer(Modifier.height(Spacing.M))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            clipboardManager.setText(AnnotatedString(packageName))
                            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(Spacing.S))
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(Spacing.XS))
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.M))

            // Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = if (isInstalledApp) stringResource(R.string.badge_installed_app) else stringResource(R.string.badge_external_file),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )

                if (splitCount > 1) {
                    StatusBadge(
                        text = stringResource(R.string.badge_splits_count, splitCount),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else if (splitCount == 1) {
                    StatusBadge(
                        text = stringResource(R.string.badge_single_apk),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // PairIP Warning
            if (isPairIpDetected) {
                Spacer(Modifier.height(Spacing.M))
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(extendedColors.warningContainer)
                        .padding(Spacing.M)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = extendedColors.warning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Spacing.S))
                        Text(
                            text = stringResource(R.string.warning_pairip),
                            style = MaterialTheme.typography.bodySmall,
                            color = extendedColors.onWarning,
                        )
                    }
                }
            }
        }
    }
}
