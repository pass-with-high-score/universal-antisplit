package app.pwhs.universalantisplit.ui.main.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.domain.signature.AppSignatureInfo
import app.pwhs.universalantisplit.domain.signature.SignatureCertificateInfo
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SignatureDetailsBottomSheet(
    signatureInfo: AppSignatureInfo?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.L)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                modifier = Modifier.fillMaxWidth()
            ) {
                val isVerified = signatureInfo?.verified == true
                val badgeColor = if (isVerified) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
                val iconTint = if (isVerified) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVerified) Icons.Rounded.Security else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.signature_details_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isVerified) {
                            stringResource(R.string.signature_status_verified)
                        } else {
                            stringResource(R.string.signature_status_unverified)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (signatureInfo == null) {
                Text(
                    text = stringResource(R.string.signature_no_signatures_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Schemes Badges
                if (signatureInfo.verifiedSchemes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Text(
                            text = stringResource(R.string.signature_schemes_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                            verticalArrangement = Arrangement.spacedBy(Spacing.XS)
                        ) {
                            signatureInfo.verifiedSchemes.forEach { scheme ->
                                StatusBadge(
                                    text = scheme,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                // Certificates Details
                signatureInfo.certificates.forEachIndexed { index, cert ->
                    CertificateCard(
                        cert = cert,
                        certIndex = if (signatureInfo.certificates.size > 1) index + 1 else null,
                        onCopySha256 = {
                            clipboardManager.setText(AnnotatedString(cert.sha256))
                            Toast.makeText(context, context.getString(R.string.signature_sha256_copied), Toast.LENGTH_SHORT).show()
                        },
                        onCopySha1 = {
                            clipboardManager.setText(AnnotatedString(cert.sha1))
                            Toast.makeText(context, context.getString(R.string.signature_sha1_copied), Toast.LENGTH_SHORT).show()
                        },
                        onCopyMd5 = {
                            clipboardManager.setText(AnnotatedString(cert.md5))
                            Toast.makeText(context, context.getString(R.string.signature_md5_copied), Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Warnings or Errors
                if (signatureInfo.errors.isNotEmpty()) {
                    OutlinedCard(
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(Spacing.M)) {
                            signatureInfo.errors.forEach { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.XS))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = stringResource(R.string.action_close),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(Spacing.M))
        }
    }
}

@Composable
private fun CertificateCard(
    cert: SignatureCertificateInfo,
    certIndex: Int?,
    onCopySha256: () -> Unit,
    onCopySha1: () -> Unit,
    onCopyMd5: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            if (certIndex != null) {
                Text(
                    text = "Chứng chỉ #$certIndex",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Fingerprints Section
            FingerprintRow(label = "SHA-256", value = cert.sha256, onCopy = onCopySha256)
            FingerprintRow(label = "SHA-1", value = cert.sha1, onCopy = onCopySha1)
            FingerprintRow(label = "MD5", value = cert.md5, onCopy = onCopyMd5)

            Spacer(Modifier.height(4.dp))

            // Metadata fields
            CertField(label = stringResource(R.string.signature_subject_label), value = cert.subject)
            CertField(label = stringResource(R.string.signature_issuer_label), value = cert.issuer)
            CertField(label = stringResource(R.string.signature_serial_label), value = cert.serialNumber)

            if (cert.validFrom != null && cert.validUntil != null) {
                CertField(
                    label = "${stringResource(R.string.signature_valid_from)} - ${stringResource(R.string.signature_valid_until)}",
                    value = "${cert.validFrom} → ${cert.validUntil}"
                )
            }

            cert.publicKeyAlgorithm?.let {
                CertField(label = stringResource(R.string.signature_public_key_algo), value = it)
            }

            cert.signatureAlgorithm?.let {
                CertField(label = stringResource(R.string.signature_algo_label), value = it)
            }
        }
    }
}

@Composable
private fun FingerprintRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCopy() }
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.S, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CertField(
    label: String,
    value: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
