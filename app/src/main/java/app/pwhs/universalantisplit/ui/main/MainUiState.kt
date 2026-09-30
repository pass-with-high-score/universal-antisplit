package app.pwhs.universalantisplit.ui.main

import android.graphics.Bitmap
import android.net.Uri
import app.pwhs.universalantisplit.data.scanner.IntegrityCheckResult
import app.pwhs.universalantisplit.domain.InstalledAppInfo
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
    val autoSignMergedApk: Boolean = true,
    val bypassSignature: Boolean = false,
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
    val integrityResult: IntegrityCheckResult? = null,
    val batchState: BatchProgressState = BatchProgressState(),
    val cacheSize: String = "0 B",
    val isClearingCache: Boolean = false,
    val isClearCacheDialogVisible: Boolean = false,
    val signatureInfo: app.pwhs.universalantisplit.domain.signature.AppSignatureInfo? = null,
    val isSignatureSheetVisible: Boolean = false,
)

sealed interface MainEvent {
    data class ShowMessage(val message: String) : MainEvent
    data class MergeCompleted(val outputPath: String, val outputFile: File, val outputUri: Uri) : MainEvent
    data class BatchMergeCompleted(val batchState: BatchProgressState) : MainEvent
}
