package app.pwhs.universalantisplit.ui.settings.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RestartAlt
import app.pwhs.universalantisplit.ui.components.AppConfirmDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.domain.KeystoreInfo
import app.pwhs.universalantisplit.theme.Spacing

@Composable
fun KeystoreSettingsCard(
    keystoreInfo: KeystoreInfo?,
    isLoading: Boolean,
    message: String?,
    onExportKeystore: (Uri) -> Unit,
    onImportKeystore: (Uri, String?, String?) -> Unit,
    onResetKeystore: () -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (!message.isNullOrBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onClearMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-pkcs12")
    ) { uri ->
        uri?.let { onExportKeystore(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { pendingImportUri = it }
    }

    // Reset confirmation dialog
    if (showResetConfirm) {
        AppConfirmDialog(
            onDismissRequest = { showResetConfirm = false },
            icon = Icons.Rounded.RestartAlt,
            isDestructive = true,
            title = stringResource(R.string.keystore_reset_confirm_title),
            message = stringResource(R.string.keystore_reset_confirm_message),
            confirmText = stringResource(R.string.action_confirm),
            cancelText = stringResource(R.string.action_cancel),
            onConfirm = {
                showResetConfirm = false
                onResetKeystore()
            },
            onCancel = { showResetConfirm = false }
        )
    }

    // Import configuration dialog
    pendingImportUri?.let { uri ->
        var password by remember { mutableStateOf("") }
        var alias by remember { mutableStateOf("") }

        AppConfirmDialog(
            onDismissRequest = { pendingImportUri = null },
            icon = Icons.Rounded.Key,
            title = stringResource(R.string.keystore_import_dialog_title),
            message = stringResource(R.string.keystore_import_dialog_desc),
            confirmText = stringResource(R.string.action_confirm),
            cancelText = stringResource(R.string.action_cancel),
            onConfirm = {
                val pass = password.trim().ifEmpty { null }
                val al = alias.trim().ifEmpty { null }
                onImportKeystore(uri, pass, al)
                pendingImportUri = null
            },
            onCancel = { pendingImportUri = null }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.keystore_password_field)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text(stringResource(R.string.keystore_alias_field)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(Spacing.M)) {
            // Header: Keystore status & badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_default_keystore_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else if (keystoreInfo != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (keystoreInfo.isCustom) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (keystoreInfo.isCustom) stringResource(R.string.keystore_badge_custom)
                            else stringResource(R.string.keystore_badge_default),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (keystoreInfo.isCustom) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.S))

            // Details
            if (keystoreInfo != null) {
                Text(
                    text = "${keystoreInfo.algorithm} • Alias: ${keystoreInfo.alias}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.keystore_valid_until, keystoreInfo.validUntil),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(Spacing.XS))

                // SHA-256 fingerprint with copy
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(keystoreInfo.sha256Fingerprint))
                            Toast.makeText(
                                context,
                                context.getString(R.string.keystore_fingerprint_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.S, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.keystore_fingerprint_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = keystoreInfo.sha256Fingerprint,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.keystore_fingerprint_copied),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.M))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("universal_antisplit.p12") },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.keystore_export_title),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.keystore_import_title),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }

                if (keystoreInfo?.isCustom == true) {
                    IconButton(
                        onClick = { showResetConfirm = true },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = stringResource(R.string.keystore_reset_default),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
