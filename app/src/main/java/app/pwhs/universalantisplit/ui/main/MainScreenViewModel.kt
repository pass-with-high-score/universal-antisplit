package app.pwhs.universalantisplit.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.data.DataRepository
import app.pwhs.universalantisplit.data.scanner.PackageScanner
import app.pwhs.universalantisplit.domain.InstalledAppInfo
import app.pwhs.universalantisplit.domain.SplitPackageInfo
import app.pwhs.universalantisplit.engine.merger.ApkMerger
import app.pwhs.universalantisplit.engine.merger.ApkOutputManager
import app.pwhs.universalantisplit.engine.merger.MergeOptions
import app.pwhs.universalantisplit.engine.merger.SplitExtractionHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

data class MainUiState(
    val selectedAppName: String? = null,
    val selectedPackageName: String? = null,
    val selectedVersionName: String? = null,
    val selectedFileSize: String? = null,
    val splitCount: Int = 0,
    val splitItems: List<String> = emptyList(),
    val selectedSplitItems: Set<String> = emptySet(),
    val isPairIpDetected: Boolean = false,
    val useRustEngine: Boolean = true,
    val autoSignMergedApk: Boolean = true,
    val isMerging: Boolean = false,
    val mergeProgress: Float = 0f,
    val mergeStatusText: String = "",
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val isAppPickerVisible: Boolean = false,
    val isInstalledApp: Boolean = false,
    val iconBitmap: Bitmap? = null,
    val lastCompletedApkFile: File? = null,
    val lastCompletedDisplayPath: String? = null,
)

sealed interface MainEvent {
    data class ShowMessage(val message: String) : MainEvent
    data class MergeCompleted(val outputPath: String, val outputFile: File) : MainEvent
}

class MainScreenViewModel(
    private val context: Context,
    private val dataRepository: DataRepository,
    private val packageScanner: PackageScanner,
    private val apkMerger: ApkMerger,
    private val apkOutputManager: ApkOutputManager,
    private val splitExtractionHelper: SplitExtractionHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var currentInstalledApp: InstalledAppInfo? = null
    private var currentExternalPackage: SplitPackageInfo? = null

    init {
        loadInstalledApps()
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
                isAppPickerVisible = false,
                isInstalledApp = true,
                iconBitmap = null,
            )
        }
        viewModelScope.launch {
            _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_selected_app, app.appName)))
        }
    }

    fun onExternalFileSelected(uri: Uri) {
        viewModelScope.launch {
            _events.send(MainEvent.ShowMessage(context.getString(R.string.status_analyzing_file)))
            val info = packageScanner.inspectExternalFile(uri)
            if (info != null) {
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
                        isInstalledApp = false,
                        iconBitmap = info.iconBitmap,
                    )
                }
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

    fun onToggleEngine(useRust: Boolean) {
        _uiState.update { it.copy(useRustEngine = useRust) }
    }

    fun onToggleAutoSign(autoSign: Boolean) {
        _uiState.update { it.copy(autoSignMergedApk = autoSign) }
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

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isMerging = true,
                    mergeProgress = 0.05f,
                    mergeStatusText = context.getString(R.string.status_analyzing_structure, toMerge.size)
                )
            }

            try {
                // 1. Prepare and extract splits
                val extracted = splitExtractionHelper.prepareForMerge(
                    isInstalledApp = state.isInstalledApp,
                    installedBaseApkPath = currentInstalledApp?.baseApkPath,
                    installedSplitPaths = currentInstalledApp?.splitPaths ?: emptyList(),
                    externalSourceUri = currentExternalPackage?.sourceUri,
                    selectedSplitNames = toMerge
                )

                val tempMergedFile = File(context.cacheDir, "merged_output_${System.currentTimeMillis()}.apk")

                // 2. Perform merge
                val mergeResult = apkMerger.merge(
                    baseApkFile = extracted.baseApk,
                    splitFiles = extracted.splitApks,
                    outputApkFile = tempMergedFile,
                    options = MergeOptions(
                        useRustEngine = state.useRustEngine,
                        autoSign = state.autoSignMergedApk
                    ),
                    onProgress = { progress, statusResId, count ->
                        val text = if (count > 0) context.getString(statusResId, count) else context.getString(statusResId)
                        _uiState.update { it.copy(mergeProgress = progress, mergeStatusText = text) }
                    }
                )

                // 3. Clean up staging directory
                splitExtractionHelper.cleanup(extracted)

                val mergedFile = mergeResult.getOrThrow()

                // 4. Save to Downloads
                val destination = apkOutputManager.saveToDownloads(
                    sourceApkFile = mergedFile,
                    baseName = state.selectedPackageName ?: appName,
                    versionName = state.selectedVersionName
                )
                mergedFile.delete()

                _uiState.update {
                    it.copy(
                        isMerging = false,
                        mergeProgress = 1.0f,
                        mergeStatusText = context.getString(R.string.status_merge_completed),
                        lastCompletedApkFile = destination.file,
                        lastCompletedDisplayPath = destination.displayPath
                    )
                }

                _events.send(MainEvent.MergeCompleted(destination.displayPath, destination.file))
            } catch (t: Throwable) {
                Timber.e(t, "Merge execution failed")
                _uiState.update {
                    it.copy(
                        isMerging = false,
                        mergeProgress = 0f,
                        mergeStatusText = ""
                    )
                }
                _events.send(MainEvent.ShowMessage(context.getString(R.string.msg_merge_failed, t.localizedMessage ?: t.message ?: "Unknown error")))
            }
        }
    }

    fun getInstallIntent(file: File) = apkOutputManager.createInstallIntent(file)

    fun getShareIntent(file: File) = apkOutputManager.createShareIntent(file)

    fun onReset() {
        currentInstalledApp = null
        currentExternalPackage = null
        _uiState.update {
            MainUiState(
                installedApps = it.installedApps,
                useRustEngine = it.useRustEngine,
                autoSignMergedApk = it.autoSignMergedApk
            )
        }
    }
}
