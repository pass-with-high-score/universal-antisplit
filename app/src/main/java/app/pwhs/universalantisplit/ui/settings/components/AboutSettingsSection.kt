package app.pwhs.universalantisplit.ui.settings.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.ui.components.SettingsSection
import coil.compose.AsyncImage

@Composable
fun AboutSettingsSection(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showDeviceInfoDialog by remember { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(R.string.settings_section_about),
        icon = Icons.Rounded.Info,
        collapsible = true,
        defaultExpanded = true,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.L, bottom = Spacing.M, start = Spacing.M, end = Spacing.M),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = R.drawable.ic_app_logo,
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.app_version_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.M)
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = Spacing.M)
            )

            // ── APP ──
            AboutSectionLabel(stringResource(R.string.about_section_app))
            AboutRow(
                icon = Icons.Rounded.Language,
                title = stringResource(R.string.about_website_title),
                subtitle = stringResource(R.string.about_website_url),
                showExternalIcon = true,
                onClick = { openUrl(context, "https://antisplit.pwhs.app") }
            )
            AboutRowDivider()
            AboutRow(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.about_project_repo),
                subtitle = stringResource(R.string.about_view_on_github),
                showExternalIcon = true,
                onClick = { openUrl(context, "https://github.com/pass-with-high-score/universal-antisplit") }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = Spacing.M, vertical = 6.dp)
            )

            // ── ECOSYSTEM ──
            AboutSectionLabel(stringResource(R.string.about_section_ecosystem))
            AboutRowImage(
                drawableRes = R.drawable.ic_universal_installer,
                title = stringResource(R.string.project_installer_title),
                subtitle = stringResource(R.string.project_installer_desc),
                onClick = { openUrl(context, "https://universal-installer.pwhs.app") }
            )
            AboutRowDivider()
            AboutRowImage(
                drawableRes = R.drawable.ic_blockads,
                title = stringResource(R.string.project_blockads_title),
                subtitle = stringResource(R.string.project_blockads_desc),
                onClick = { openUrl(context, "https://github.com/pass-with-high-score/blockads-android") }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = Spacing.M, vertical = 6.dp)
            )

            // ── COMMUNITY ──
            AboutSectionLabel(stringResource(R.string.about_section_community))
            AboutRowPainter(
                iconPainter = painterResource(R.drawable.ic_telegram),
                iconTint = Color(0xFF0088CC),
                title = stringResource(R.string.about_telegram_title),
                subtitle = stringResource(R.string.about_telegram_desc),
                showExternalIcon = true,
                onClick = { openUrl(context, "https://t.me/blockads_android") }
            )
            AboutRowDivider()
            AboutRow(
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.about_creator_title),
                subtitle = stringResource(R.string.about_creator_subtitle),
                showExternalIcon = true,
                onClick = { openUrl(context, "https://github.com/pass-with-high-score") }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = Spacing.M, vertical = 6.dp)
            )

            // ── LEGAL ──
            AboutSectionLabel(stringResource(R.string.about_section_legal))
            AboutRow(
                icon = Icons.Rounded.Description,
                title = stringResource(R.string.about_license_title),
                subtitle = stringResource(R.string.about_license_subtitle),
                showExternalIcon = true,
                onClick = { openUrl(context, "https://github.com/pass-with-high-score/universal-antisplit/blob/main/LICENSE") }
            )
            AboutRowDivider()
            AboutRow(
                icon = Icons.Rounded.Policy,
                title = stringResource(R.string.about_privacy_title),
                subtitle = stringResource(R.string.about_privacy_subtitle),
                showExternalIcon = true,
                onClick = { openUrl(context, "https://antisplit.pwhs.app/privacy") }
            )
            AboutRowDivider()
            AboutRow(
                icon = Icons.Rounded.Gavel,
                title = stringResource(R.string.about_terms_title),
                subtitle = stringResource(R.string.about_terms_subtitle),
                showExternalIcon = true,
                onClick = { openUrl(context, "https://antisplit.pwhs.app/terms") }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = Spacing.M, vertical = 6.dp)
            )

            // ── DEVICE ──
            AboutSectionLabel(stringResource(R.string.about_section_device))
            AboutRow(
                icon = Icons.Rounded.Smartphone,
                title = stringResource(R.string.about_device_info_title),
                subtitle = "${Build.MANUFACTURER} ${Build.MODEL}",
                showExternalIcon = false,
                onClick = { showDeviceInfoDialog = true }
            )

            Spacer(Modifier.height(Spacing.M))
        }
    }

    if (showDeviceInfoDialog) {
        DeviceInfoDialog(onDismiss = { showDeviceInfoDialog = false })
    }
}

@Composable
private fun AboutSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = Spacing.L, top = Spacing.M, bottom = 4.dp),
    )
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    showExternalIcon: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.L, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(Spacing.M))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showExternalIcon) {
            Spacer(Modifier.width(Spacing.S))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AboutRowPainter(
    iconPainter: Painter,
    title: String,
    subtitle: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    showExternalIcon: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.L, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(Spacing.M))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showExternalIcon) {
            Spacer(Modifier.width(Spacing.S))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AboutRowImage(
    @DrawableRes drawableRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.L, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = drawableRes,
            contentDescription = title,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(Spacing.M))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.S))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AboutRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}

@Composable
private fun DeviceInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    val deviceInfo = remember {
        buildString {
            appendLine(context.getString(R.string.about_device_manufacturer, Build.MANUFACTURER))
            appendLine(context.getString(R.string.about_device_model, Build.MODEL))
            appendLine(context.getString(R.string.about_device_sdk, Build.VERSION.SDK_INT.toString()))
            appendLine(context.getString(R.string.about_device_os, Build.VERSION.RELEASE))
            appendLine(context.getString(R.string.about_device_arch, Build.SUPPORTED_ABIS.joinToString(", ")))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_device_info_title)) },
        text = {
            Box(Modifier.heightIn(max = 350.dp)) {
                LazyColumn {
                    item {
                        Text(
                            text = deviceInfo,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_btn_ok)) }
        },
        dismissButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("device-info", deviceInfo)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, R.string.about_copied, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.about_btn_copy)) }
        },
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
