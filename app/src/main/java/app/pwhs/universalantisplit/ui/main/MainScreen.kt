package app.pwhs.universalantisplit.ui.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.Settings
import app.pwhs.universalantisplit.theme.LocalExtendedColors
import app.pwhs.universalantisplit.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import app.pwhs.universalantisplit.ui.components.AppPickerSheet
import app.pwhs.universalantisplit.ui.components.EmptyStateView
import app.pwhs.universalantisplit.ui.components.SettingsSection
import app.pwhs.universalantisplit.ui.main.components.MergeProgressSheet
import app.pwhs.universalantisplit.ui.main.components.MergeSuccessDialog
import app.pwhs.universalantisplit.ui.main.components.SelectedAppCard
import app.pwhs.universalantisplit.ui.main.components.SplitComponentsCard
import java.io.File
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extendedColors = LocalExtendedColors.current

    var successDialogData by remember { mutableStateOf<Pair<String, File>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onExternalFileSelected(uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MainEvent.ShowMessage -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is MainEvent.MergeCompleted -> {
                    successDialogData = Pair(event.outputPath, event.outputFile)
                }
            }
        }
    }

    val mergeSheetState = rememberModalBottomSheetState()

    MergeProgressSheet(
        isMerging = state.isMerging,
        progress = state.mergeProgress,
        statusText = state.mergeStatusText,
        sheetState = mergeSheetState,
    )

    if (state.isAppPickerVisible) {
        AppPickerSheet(
            apps = state.installedApps,
            isLoading = state.isLoadingApps,
            onDismiss = { viewModel.showAppPicker(false) },
            onAppSelected = { app -> viewModel.onInstalledAppSelected(app) }
        )
    }

    successDialogData?.let { (path, file) ->
        MergeSuccessDialog(
            outputPath = path,
            outputFile = file,
            onInstall = {
                runCatching {
                    context.startActivity(viewModel.getInstallIntent(it))
                }
            },
            onShare = {
                runCatching {
                    val shareIntent = Intent.createChooser(viewModel.getShareIntent(it), null)
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)
                }
            },
            onDismiss = { successDialogData = null }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.M)
                    ) {
                        AsyncImage(
                            model = R.drawable.ic_app_logo,
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }

            // File selection banner / Empty state
            item {
                if (state.selectedAppName == null) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.L),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            EmptyStateView(
                                icon = Icons.Rounded.Layers,
                                title = stringResource(R.string.empty_state_title),
                                subtitle = stringResource(R.string.empty_state_subtitle),
                            )

                            Spacer(Modifier.height(Spacing.L))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.M)
                            ) {
                                Button(
                                    onClick = { viewModel.showAppPicker(true) },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Apps,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(Spacing.S))
                                    Text(stringResource(R.string.btn_installed_apps), maxLines = 1)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        filePickerLauncher.launch(
                                            arrayOf("*/*", "application/vnd.android.package-archive", "application/zip", "application/octet-stream")
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(Spacing.S))
                                    Text(stringResource(R.string.btn_pick_external_file), maxLines = 1)
                                }
                            }
                        }
                    }
                } else {
                    SelectedAppCard(
                        appName = state.selectedAppName ?: "",
                        packageName = state.selectedPackageName,
                        versionName = state.selectedVersionName,
                        fileSize = state.selectedFileSize,
                        splitCount = state.splitCount,
                        isPairIpDetected = state.isPairIpDetected,
                        isInstalledApp = state.isInstalledApp,
                        iconBitmap = state.iconBitmap,
                        onChangeClick = { viewModel.onReset() },
                        extendedColors = extendedColors,
                        integrityResult = state.integrityResult,
                    )
                }
            }

            // Split items list if selected
            if (state.splitItems.isNotEmpty()) {
                item {
                    SplitComponentsCard(
                        splitItems = state.splitItems,
                        selectedSplitItems = state.selectedSplitItems,
                        onToggleSplitItem = { viewModel.onToggleSplitItem(it) },
                        onSelectAllSplits = { viewModel.onSelectAllSplits(it) }
                    )
                }
            }

            // Signature Settings
            item {
                SettingsSection(
                    title = stringResource(R.string.section_engine_signature),
                    icon = Icons.Rounded.Tune,
                    collapsible = true,
                    defaultExpanded = true,
                ) {
                    Column(modifier = Modifier.padding(Spacing.L)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.setting_auto_sign_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.setting_auto_sign_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.autoSignMergedApk,
                                onCheckedChange = { viewModel.onToggleAutoSign(it) }
                            )
                        }

                        Spacer(Modifier.height(Spacing.L))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_bypass_signature_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.settings_bypass_signature_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.bypassSignature,
                                onCheckedChange = { viewModel.onToggleBypassSignature(it) }
                            )
                        }
                    }
                }
            }

            // Action button
            if (state.selectedAppName != null) {
                item {
                    Button(
                        onClick = { viewModel.onStartMerge() },
                        enabled = !state.isMerging && state.selectedSplitItems.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Spacing.S))
                        Text(
                            text = if (state.isMerging) stringResource(R.string.btn_merging) else stringResource(R.string.btn_start_merge, state.selectedSplitItems.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}
