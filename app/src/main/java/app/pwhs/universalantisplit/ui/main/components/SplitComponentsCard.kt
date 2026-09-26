package app.pwhs.universalantisplit.ui.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.theme.Spacing
import app.pwhs.universalantisplit.ui.components.SettingsSection
import app.pwhs.universalantisplit.ui.components.StatusBadge

@Composable
fun SplitComponentsCard(
    splitItems: List<String>,
    selectedSplitItems: Set<String>,
    onToggleSplitItem: (String) -> Unit,
    onSelectAllSplits: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(R.string.section_components_title, selectedSplitItems.size, splitItems.size),
        icon = Icons.Rounded.Layers,
        collapsible = true,
        defaultExpanded = true,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(Spacing.L)) {
            // Quick select chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedSplitItems.size == splitItems.size,
                    onClick = { onSelectAllSplits(true) },
                    label = { Text(stringResource(R.string.chip_select_all, splitItems.size)) },
                    shape = MaterialTheme.shapes.small
                )
                FilterChip(
                    selected = selectedSplitItems.size < splitItems.size,
                    onClick = { onSelectAllSplits(false) },
                    label = { Text(stringResource(R.string.chip_core_only)) },
                    shape = MaterialTheme.shapes.small
                )
            }

            Spacer(Modifier.height(Spacing.M))

            splitItems.forEach { item ->
                val isBase = item.startsWith("base.apk") || item.equals("base.apk", ignoreCase = true)
                val isChecked = isBase || (item in selectedSplitItems)

                val typeBadgeRes = when {
                    isBase -> R.string.badge_core_required
                    item.contains("arm", ignoreCase = true) || item.contains("x86", ignoreCase = true) -> R.string.badge_abi_native
                    item.contains("en", ignoreCase = true) || item.contains("vi", ignoreCase = true) ||
                            item.contains("zh", ignoreCase = true) || item.contains("es", ignoreCase = true) ||
                            item.contains("fr", ignoreCase = true) || item.contains("de", ignoreCase = true) ||
                            item.contains("ja", ignoreCase = true) || item.contains("ko", ignoreCase = true) -> R.string.badge_language
                    item.contains("hdpi", ignoreCase = true) || item.contains("nodpi", ignoreCase = true) -> R.string.badge_screen_density
                    else -> R.string.badge_auxiliary
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .then(
                            if (!isBase) Modifier.clickable { onToggleSplitItem(item) }
                            else Modifier
                        )
                        .padding(vertical = Spacing.XS, horizontal = Spacing.XS)
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { if (!isBase) onToggleSplitItem(item) },
                        enabled = !isBase
                    )
                    Spacer(Modifier.width(Spacing.S))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isBase) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(Spacing.S))
                    StatusBadge(
                        text = stringResource(typeBadgeRes),
                        containerColor = if (isBase) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (isBase) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
