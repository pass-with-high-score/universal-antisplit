package app.pwhs.universalantisplit.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    apps: List<InstalledAppInfo>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAppSelected: (InstalledAppInfo) -> Unit,
    onBatchMergeSelected: (List<InstalledAppInfo>) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var onlySplits by remember { mutableStateOf(false) }
    var isMultiSelect by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }

    val filteredApps = remember(apps, searchQuery, onlySplits) {
        apps.filter { app ->
            val matchSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchSplit = !onlySplits || app.isSplitApp
            matchSearch && matchSplit
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = Spacing.L)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.picker_subtitle, apps.size, apps.count { it.isSplitApp }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            Spacer(Modifier.height(Spacing.M))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.picker_search_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            Spacer(Modifier.height(Spacing.S))

            // Filter Chips & Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FilterChip(
                        selected = !onlySplits,
                        onClick = { onlySplits = false },
                        label = { Text(stringResource(R.string.picker_filter_all, apps.size)) },
                        shape = MaterialTheme.shapes.small
                    )
                    FilterChip(
                        selected = onlySplits,
                        onClick = { onlySplits = true },
                        label = { Text(stringResource(R.string.picker_filter_only_splits, apps.count { it.isSplitApp })) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = MaterialTheme.shapes.small
                    )
                }

                FilterChip(
                    selected = isMultiSelect,
                    onClick = {
                        isMultiSelect = !isMultiSelect
                        if (!isMultiSelect) selectedPackages = emptySet()
                    },
                    label = {
                        Text(
                            text = if (isMultiSelect) stringResource(R.string.picker_mode_single_select) else stringResource(R.string.picker_mode_multi_select)
                        )
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = MaterialTheme.shapes.small
                )
            }

            if (isMultiSelect) {
                Spacer(Modifier.height(Spacing.XS))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.picker_selected_count, selectedPackages.size),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = {
                            selectedPackages = if (selectedPackages.size == filteredApps.size) {
                                emptySet()
                            } else {
                                filteredApps.map { it.packageName }.toSet()
                            }
                        }
                    ) {
                        Text(
                            text = if (selectedPackages.size == filteredApps.size && filteredApps.isNotEmpty()) {
                                stringResource(R.string.picker_deselect_all)
                            } else {
                                stringResource(R.string.picker_select_all)
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.M))
            HorizontalDivider()

            // App list or loading
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(Spacing.M))
                        Text(stringResource(R.string.picker_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName in selectedPackages
                        AppListItem(
                            app = app,
                            isMultiSelect = isMultiSelect,
                            isSelected = isSelected,
                            onClick = {
                                if (isMultiSelect) {
                                    selectedPackages = if (isSelected) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                } else {
                                    onAppSelected(app)
                                }
                            }
                        )
                    }
                }

                if (isMultiSelect) {
                    Button(
                        onClick = {
                            val selected = apps.filter { it.packageName in selectedPackages }
                            onBatchMergeSelected(selected)
                        },
                        enabled = selectedPackages.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.M)
                            .height(50.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(imageVector = Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.S))
                        Text(
                            text = stringResource(R.string.picker_btn_merge_selected, selectedPackages.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: InstalledAppInfo,
    isMultiSelect: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.M, horizontal = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!.asImageBitmap(),
                contentDescription = app.appName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.width(Spacing.M))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                Text(
                    text = "${app.versionName} • ${app.sizeFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (app.isSplitApp) {
                    StatusBadge(
                        text = "${app.splitPaths.size} Splits",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (isMultiSelect) {
            Spacer(Modifier.width(Spacing.S))
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
        }
    }
}
