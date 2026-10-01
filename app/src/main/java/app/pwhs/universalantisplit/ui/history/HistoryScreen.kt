package app.pwhs.universalantisplit.ui.history

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import app.pwhs.universalantisplit.ui.components.AppConfirmDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.data.local.db.entity.ConversionHistory
import app.pwhs.universalantisplit.ui.history.components.HistoryFilterChips
import app.pwhs.universalantisplit.ui.history.components.HistoryItemActionsSheet
import app.pwhs.universalantisplit.ui.history.components.HistoryItemCard
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    var selectedItem by remember { mutableStateOf<ConversionHistory?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.InstallFailed -> Toast.makeText(
                    context,
                    context.getString(R.string.install_failed, event.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()

                HistoryEvent.FileUnavailable -> Toast.makeText(
                    context,
                    context.getString(R.string.history_file_not_found),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    selectedItem?.let { item ->
        val apkInfo by produceState<app.pwhs.universalantisplit.domain.ApkFileInfo?>(
            initialValue = null,
            key1 = item.id
        ) {
            value = if (item.isSuccessful) viewModel.readApkInfo(item.outputPath) else null
        }

        fun startOrToast(buildIntent: (java.io.File) -> Intent, fallbackRes: Int) {
            val file = viewModel.resolveApkFile(item.outputPath)
            if (file != null && file.exists()) {
                runCatching {
                    context.startActivity(buildIntent(file).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }.onFailure {
                    Toast.makeText(context, context.getString(fallbackRes), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, context.getString(R.string.history_file_not_found), Toast.LENGTH_SHORT).show()
            }
        }

        HistoryItemActionsSheet(
            item = item,
            apkInfo = apkInfo,
            onInstall = { viewModel.installApk(item.outputPath) },
            onShare = {
                val file = viewModel.resolveApkFile(item.outputPath)
                if (file != null && file.exists()) {
                    context.startActivity(
                        Intent.createChooser(viewModel.createShareIntent(file), null)
                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    )
                } else {
                    Toast.makeText(context, context.getString(R.string.history_file_not_found), Toast.LENGTH_SHORT).show()
                }
            },
            onOpenFolder = { startOrToast(viewModel::createOpenFolderIntent, R.string.history_no_folder_app) },
            onCopyPath = {
                clipboard.setText(AnnotatedString(item.outputPath))
                Toast.makeText(
                    context,
                    context.getString(R.string.msg_copied_package, item.outputPath),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDelete = { viewModel.deleteItem(item) },
            onDismissRequest = { selectedItem = null }
        )
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.onSearchQueryChanged("")
    }

    if (uiState.showClearConfirm) {
        AppConfirmDialog(
            onDismissRequest = { viewModel.showClearConfirm(false) },
            icon = Icons.Rounded.DeleteForever,
            isDestructive = true,
            title = stringResource(R.string.history_confirm_clear_title),
            message = stringResource(R.string.history_confirm_clear_message),
            confirmText = stringResource(R.string.action_confirm),
            cancelText = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.clearAll() },
            onCancel = { viewModel.showClearConfirm(false) }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.history_search_placeholder),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.onSearchQueryChanged("")
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_nav_back)
                            )
                        }
                    },
                    actions = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_clear)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.history_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.settings_nav_back)
                                )
                            }
                        }
                    },
                    actions = {
                        if (uiState.allHistoryList.isNotEmpty()) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.history_search_action)
                                )
                            }
                            IconButton(onClick = { viewModel.showClearConfirm(true) }) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = stringResource(R.string.history_clear_all)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.allHistoryList.isNotEmpty()) {
                HistoryFilterChips(
                    statusFilter = uiState.statusFilter,
                    onStatusFilterChanged = { viewModel.onStatusFilterChanged(it) },
                    sourceFilter = uiState.sourceFilter,
                    onSourceFilterChanged = { viewModel.onSourceFilterChanged(it) }
                )
            }

            if (uiState.allHistoryList.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.history_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else if (uiState.filteredHistoryList.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.history_no_results_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.history_no_results_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = {
                            isSearchActive = false
                            viewModel.resetFilters()
                        }) {
                            Text(
                                text = stringResource(R.string.history_clear_filters),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredHistoryList, key = { it.id }) { item ->
                        HistoryItemCard(
                            item = item,
                            onClick = { selectedItem = item }
                        )
                    }
                }
            }
        }
    }
}
