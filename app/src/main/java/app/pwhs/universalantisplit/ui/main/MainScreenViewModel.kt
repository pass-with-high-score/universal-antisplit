package app.pwhs.universalantisplit.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.data.DataRepository
import app.pwhs.universalantisplit.data.cache.AppCacheManager
import app.pwhs.universalantisplit.data.local.PreferenceKeys
import app.pwhs.universalantisplit.data.local.dataStore
import app.pwhs.universalantisplit.data.repository.HistoryRepository
import app.pwhs.universalantisplit.data.scanner.IntegrityScanner
import app.pwhs.universalantisplit.data.scanner.PackageScanner
import app.pwhs.universalantisplit.domain.InstalledAppInfo
import app.pwhs.universalantisplit.domain.SplitPackageInfo
import app.pwhs.universalantisplit.engine.merger.ApkMerger
import app.pwhs.universalantisplit.engine.merger.ApkOutputManager
import app.pwhs.universalantisplit.engine.merger.MergeOptions
import app.pwhs.universalantisplit.engine.merger.SplitExtractionHelper
import app.pwhs.universalantisplit.engine.signature.ApkSignatureReader
import app.pwhs.universalantisplit.protocol.UniversalInstallerProtocol
import app.pwhs.universalantisplit.worker.ApkMergerWorker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainScreenViewModel(
    private val context: Context,
    private val dataRepository: DataRepository,
    private val packageScanner: PackageScanner,
    private val apkMerger: ApkMerger,
    private val apkOutputManager: ApkOutputManager,
    private val splitExtractionHelper: SplitExtractionHelper,
    private val historyRepository: HistoryRepository,
    private val workManager: WorkManager,
    private val appCacheManager: AppCacheManager,
    private val apkInstaller: app.pwhs.universalantisplit.engine.ApkInstaller,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var currentInstalledApp: InstalledAppInfo? = null
    private var currentExternalPackage: SplitPackageInfo? = null

    init {
        loadInstalledApps()
        observeMergeWorker()
        observeSettings()
        refreshCacheSize()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            context.dataStore.data.collect { prefs ->
                val autoSign = prefs[PreferenceKeys.AUTO_SIGN] ?: true
                val bypassSig = prefs[PreferenceKeys.BYPASS_SIGNATURE] ?: false
                _uiState.update { it.copy(autoSignMergedApk = autoSign, bypassSignature = bypassSig) }
            }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = packageScanner.getInstalledApps()
            _uiState.update { it.copy(installedApps = apps, isLoadingApps = false) }
        }
    }

    fun showAppPicker(show: Boolean) {
        _uiState.update { it.copy(isAppPickerVisible = show) }
        if (show && _uiState.value.installedApps.isEmpty()) {
            loadInstalledApps()
        }
    }

    fun onInstalledAppSelected(app: InstalledAppInfo) {
        currentInstalledApp = app
        currentExternalPackage = null

        val splits = mutableListOf<String>()
        splits.add("base.apk (Main)")
        app.splitPaths.forEach { path ->
            splits.add(File(path).name)
        }

        val filesToScan = mutableListOf<File>()
        if (app.baseApkPath.isNotEmpty()) filesToScan.add(File(app.baseApkPath))
        app.splitPaths.forEach { filesToScan.add(File(it)) }

        _uiState.update {
            it.copy(
                selectedAppName = app.appName,
                selectedPackageName = app.packageName,
                selectedVersionName = app.versionName,
                selectedFileSize = app.sizeFormatted,
                splitCount = splits.size,
                splitItems = splits,
                selectedSplitItems = splits.toSet(),
                isPairIpDetected = false,
                integrityResult = null,
                isAppPickerVisible = false,
                isInstalledApp = true,
                iconBitmap = null,
                isMerging = false,
            )
        }
        viewModelScope.launch {
            _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_selected_app, app.appName)))
            val scanResult = withContext(Dispatchers.IO) { IntegrityScanner.scanApkFiles(filesToScan) }
            val sigInfo = withContext(Dispatchers.IO) {
                ApkSignatureReader.readFromInstalledPackage(context, app.packageName)
                    ?: filesToScan.firstOrNull()?.let { ApkSignatureReader.readFromApkFile(it) }
            }
            _uiState.update {
                it.copy(
                    integrityResult = scanResult,
                    signatureInfo = sigInfo,
                    isPairIpDetected = scanResult.isPairIpDetected,
                    bypassSignature = scanResult.isPairIpDetected,
                )
            }
        }
    }

    private fun applyExternalPackage(info: SplitPackageInfo) {
        currentExternalPackage = info
        currentInstalledApp = null
        _uiState.update {
            it.copy(
                selectedAppName = info.appName ?: info.name,
                selectedPackageName = info.packageName,
                selectedVersionName = info.versionName,
                selectedFileSize = info.sizeFormatted,
                splitCount = info.splitNames.size,
                splitItems = info.splitNames,
                selectedSplitItems = info.splitNames.toSet(),
                isPairIpDetected = info.isPairIpDetected,
                integrityResult = null,
                signatureInfo = null,
                bypassSignature = info.isPairIpDetected,
                isInstalledApp = false,
                iconBitmap = info.iconBitmap,
                isMerging = false,
            )
        }
        val baseFile = info.baseApkPath?.let { File(it) }
        if (baseFile != null && baseFile.exists()) {
            viewModelScope.launch {
                val sig = withContext(Dispatchers.IO) { ApkSignatureReader.readFromApkFile(baseFile) }
                _uiState.update { it.copy(signatureInfo = sig) }
            }
        }
    }

    fun onExternalFileSelected(uri: Uri) {
        viewModelScope.launch {
            _events.send(MainEvent.ShowMessage(context.getString(R.string.status_analyzing_file)))
            val info = packageScanner.inspectExternalFile(uri)
            if (info != null) {
                applyExternalPackage(info)
                _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_loaded_file, info.appName ?: info.name)))
            } else {
                _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_cannot_read_file)))
            }
        }
    }

    fun onToggleSplitItem(item: String) {
        if (item.startsWith("base.apk") || item.equals("base.apk", ignoreCase = true)) {
            return
        }
        _uiState.update { current ->
            val updated = if (item in current.selectedSplitItems) {
                current.selectedSplitItems - item
            } else {
                current.selectedSplitItems + item
            }
            current.copy(selectedSplitItems = updated)
        }
    }

    fun onSelectAllSplits(select: Boolean) {
        _uiState.update { current ->
            val updated = if (select) {
                current.splitItems.toSet()
            } else {
                current.splitItems.filter { it.startsWith("base.apk") || it.equals("base.apk", ignoreCase = true) }.toSet()
            }
            current.copy(selectedSplitItems = updated)
        }
    }

    fun onToggleAutoSign(autoSign: Boolean) {
        _uiState.update { it.copy(autoSignMergedApk = autoSign) }
    }

    fun onToggleBypassSignature(bypass: Boolean) {
        _uiState.update { it.copy(bypassSignature = bypass) }
        if (bypass && !_uiState.value.isPairIpDetected && _uiState.value.selectedAppName != null) {
            viewModelScope.launch {
                _events.send(MainEvent.ShowMessage(context.getString(R.string.warning_pairip_not_detected_toggle)))
            }
        }
    }

    private fun observeMergeWorker() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(ApkMergerWorker.WORK_NAME).collect { workInfos ->
                val workInfo = workInfos.firstOrNull() ?: return@collect
                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        if (_uiState.value.isMerging) {
                            _uiState.update {
                                it.copy(
                                    mergeProgress = 0.05f,
                                    mergeStatusText = context.getString(R.string.status_analyzing_structure, it.selectedSplitItems.size)
                                )
                            }
                        }
                    }
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getFloat(ApkMergerWorker.KEY_PROGRESS, 0.05f)
                        val statusText = workInfo.progress.getString(ApkMergerWorker.KEY_STATUS_TEXT)
                            ?: context.getString(R.string.status_merging_resources)
                        _uiState.update {
                            it.copy(
                                isMerging = true,
                                mergeProgress = progress,
                                mergeStatusText = statusText
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val wasMerging = _uiState.value.isMerging
                        val outPath = workInfo.outputData.getString(ApkMergerWorker.KEY_OUTPUT_FILE_PATH)
                        val displayPath = workInfo.outputData.getString(ApkMergerWorker.KEY_DISPLAY_PATH) ?: outPath ?: ""
                        val outFile = outPath?.let { File(it) }
                        _uiState.update {
                            it.copy(
                                isMerging = false,
                                mergeProgress = 1.0f,
                                mergeStatusText = context.getString(R.string.status_merge_completed),
                                lastCompletedApkFile = outFile,
                                lastCompletedDisplayPath = displayPath
                            )
                        }
                        if (wasMerging && outFile != null) {
                            val uri = try {
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    outFile
                                )
                            } catch (e: Exception) {
                                Uri.fromFile(outFile)
                            }
                            _events.send(MainEvent.MergeCompleted(displayPath, outFile, uri))
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val wasMerging = _uiState.value.isMerging
                        val errorMsg = workInfo.outputData.getString(ApkMergerWorker.KEY_ERROR_MESSAGE)
                            ?: context.getString(R.string.msg_merge_failed, "Unknown error")
                        _uiState.update {
                            it.copy(
                                isMerging = false,
                                mergeProgress = 0f,
                                mergeStatusText = ""
                            )
                        }
                        if (wasMerging) {
                            _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_merge_failed, errorMsg)))
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        _uiState.update {
                            it.copy(
                                isMerging = false,
                                mergeProgress = 0f,
                                mergeStatusText = ""
                            )
                        }
                    }
                }
            }
        }
    }

    fun onStartMerge() {
        val state = _uiState.value
        val appName = state.selectedAppName
        if (appName == null) {
            viewModelScope.launch {
                _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_please_select_file)))
            }
            return
        }

        val toMerge = state.selectedSplitItems
        if (toMerge.isEmpty()) {
            viewModelScope.launch {
                _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_please_select_component)))
            }
            return
        }

        _uiState.update {
            it.copy(
                isMerging = true,
                mergeProgress = 0.05f,
                mergeStatusText = context.getString(R.string.status_analyzing_structure, toMerge.size)
            )
        }

        val inputData = workDataOf(
            ApkMergerWorker.KEY_IS_INSTALLED_APP to state.isInstalledApp,
            ApkMergerWorker.KEY_INSTALLED_BASE_APK_PATH to currentInstalledApp?.baseApkPath,
            ApkMergerWorker.KEY_INSTALLED_SPLIT_PATHS to (currentInstalledApp?.splitPaths ?: emptyList()).toTypedArray(),
            ApkMergerWorker.KEY_EXTERNAL_SOURCE_URI to currentExternalPackage?.sourceUri?.toString(),
            ApkMergerWorker.KEY_SELECTED_SPLIT_NAMES to toMerge.toTypedArray(),
            ApkMergerWorker.KEY_AUTO_SIGN to state.autoSignMergedApk,
            ApkMergerWorker.KEY_BYPASS_SIGNATURE to state.bypassSignature,
            ApkMergerWorker.KEY_APP_NAME to appName,
            ApkMergerWorker.KEY_PACKAGE_NAME to state.selectedPackageName,
            ApkMergerWorker.KEY_VERSION_NAME to state.selectedVersionName
        )

        val workRequest = OneTimeWorkRequestBuilder<ApkMergerWorker>()
            .setInputData(inputData)
            .addTag("apk_merge")
            .build()

        workManager.enqueueUniqueWork(
            ApkMergerWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun onCancelMerge() {
        workManager.cancelUniqueWork(ApkMergerWorker.WORK_NAME)
        _uiState.update {
            it.copy(
                isMerging = false,
                mergeProgress = 0f,
                mergeStatusText = ""
            )
        }
    }

    private val batchMergeHandler = BatchMergeHandler(
        context = context,
        workManager = workManager,
        scope = viewModelScope,
        uiStateFlow = _uiState,
        sendEvent = { _events.send(it) }
    )

    fun onBatchMergeApps(apps: List<InstalledAppInfo>) {
        if (apps.isEmpty()) return
        showAppPicker(false)

        if (apps.size == 1) {
            onInstalledAppSelected(apps.first())
            onStartMerge()
            return
        }

        viewModelScope.launch {
            _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_batch_merge_started, apps.size)))
        }

        batchMergeHandler.startBatchMerge(
            apps = apps,
            autoSign = _uiState.value.autoSignMergedApk,
            bypassSignature = false
        )
    }

    fun setBatchSheetVisible(visible: Boolean) {
        batchMergeHandler.setSheetVisible(visible)
    }

    fun onCancelBatchMerge() {
        batchMergeHandler.cancelBatch()
    }

    fun onDismissBatchSummary() {
        batchMergeHandler.dismissBatch()
    }

    private val externalIntentHandler = ExternalIntentHandler(context, packageScanner)

    /**
     * Handles incoming intent from external apps, file managers, or Universal Installer.
     */
    fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        viewModelScope.launch {
            _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_loading_intent_source)))
            when (val result = externalIntentHandler.processIntent(intent, onAutoSignOverride = { onToggleAutoSign(it) })) {
                is IntentProcessResult.TargetAppFound -> {
                    onInstalledAppSelected(result.app)
                    if (result.autoStart) onStartMerge()
                }
                is IntentProcessResult.ExternalFileLoaded -> {
                    applyExternalPackage(result.info)
                    _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_file_received_from_app, result.info.appName ?: result.info.name)))
                    if (result.autoStart) onStartMerge()
                }
                is IntentProcessResult.Message -> {
                    _events.send(MainEvent.ShowMessage(result.message))
                }
                null -> {}
            }
        }
    }

    fun setSignatureSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isSignatureSheetVisible = visible) }
    }

    fun inspectApkSignature(file: File) {
        viewModelScope.launch {
            val sig = withContext(Dispatchers.IO) { ApkSignatureReader.readFromApkFile(file) }
            _uiState.update { it.copy(signatureInfo = sig, isSignatureSheetVisible = true) }
        }
    }

    fun installApk(file: File) {
        val uri = apkOutputManager.resolveApkUri(file.absolutePath)
        if (uri == null) {
            viewModelScope.launch {
                _events.send(MainEvent.ShowMessage(context.getString(R.string.history_file_not_found)))
            }
            return
        }
        viewModelScope.launch {
            runCatching { apkInstaller.install(uri) }.onFailure {
                _events.send(MainEvent.ShowMessage(context.getString(R.string.install_failed, it.message ?: "")))
            }
        }
    }

    fun getShareIntent(file: File) = apkOutputManager.createShareIntent(file)

    fun onReset() {
        currentInstalledApp = null
        currentExternalPackage = null
        _uiState.update {
            MainUiState(
                installedApps = it.installedApps,
                autoSignMergedApk = it.autoSignMergedApk,
                bypassSignature = it.bypassSignature,
            )
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val breakdown = appCacheManager.getCacheBreakdown()
            _uiState.update { it.copy(cacheSize = breakdown.formattedTotalSize) }
        }
    }

    fun showClearCacheDialog(visible: Boolean) {
        if (visible) refreshCacheSize()
        _uiState.update { it.copy(isClearCacheDialogVisible = visible) }
    }

    fun clearCache(includeLocalMerged: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingCache = true) }
            val bytesFreed = appCacheManager.clearCache(includeLocalMerged)
            val breakdown = appCacheManager.getCacheBreakdown()
            _uiState.update {
                it.copy(
                    isClearingCache = false,
                    cacheSize = breakdown.formattedTotalSize,
                    isClearCacheDialogVisible = false
                )
            }
            _events.send(MainEvent.ShowMessage(context.getString(R.string.home_cache_freed_message, appCacheManager.formatBytes(bytesFreed))))
        }
    }
}
