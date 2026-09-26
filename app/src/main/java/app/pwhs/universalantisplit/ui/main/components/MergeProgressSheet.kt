package app.pwhs.universalantisplit.ui.main.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeProgressSheet(
    isMerging: Boolean,
    progress: Float,
    statusText: String,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    if (!isMerging) return

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 400),
        label = "merge_progress"
    )
    val percent = (animatedProgress * 100).toInt().coerceIn(0, 100)

    ModalBottomSheet(
        onDismissRequest = { /* non-dismissible while merging */ },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XL, vertical = Spacing.XL)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            // Header: icon + title + percent
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(Spacing.M))
                    Text(
                        text = stringResource(R.string.merge_progress_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.merge_progress_percent, percent),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            // Step label
            if (statusText.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.merge_progress_step_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(Spacing.XS))

            // Warning note
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(Spacing.S))
                Text(
                    text = stringResource(R.string.merge_progress_please_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
