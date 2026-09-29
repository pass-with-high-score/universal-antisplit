package app.pwhs.universalantisplit.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.data.local.db.entity.ConversionHistory
import app.pwhs.universalantisplit.data.repository.HistoryRepository
import app.pwhs.universalantisplit.engine.merger.ApkMerger
import app.pwhs.universalantisplit.engine.merger.ApkOutputManager
import app.pwhs.universalantisplit.engine.merger.ExtractedSplits
import app.pwhs.universalantisplit.engine.merger.MergeOptions
import app.pwhs.universalantisplit.engine.merger.SplitExtractionHelper
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File

class ApkMergerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val apkMerger: ApkMerger by inject()
    private val splitExtractionHelper: SplitExtractionHelper by inject()
    private val apkOutputManager: ApkOutputManager by inject()
    private val historyRepository: HistoryRepository by inject()

    companion object {
        const val WORK_NAME = "apk_merge_work"

        const val KEY_IS_INSTALLED_APP = "is_installed_app"
        const val KEY_INSTALLED_BASE_APK_PATH = "installed_base_apk_path"
        const val KEY_INSTALLED_SPLIT_PATHS = "installed_split_paths"
        const val KEY_EXTERNAL_SOURCE_URI = "external_source_uri"
        const val KEY_SELECTED_SPLIT_NAMES = "selected_split_names"
        const val KEY_AUTO_SIGN = "auto_sign"
        const val KEY_BYPASS_SIGNATURE = "bypass_signature"
        const val KEY_APP_NAME = "app_name"
        const val KEY_PACKAGE_NAME = "package_name"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_ICON_URI = "icon_uri"

        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS_TEXT = "status_text"
        const val KEY_OUTPUT_FILE_PATH = "output_file_path"
        const val KEY_DISPLAY_PATH = "display_path"
        const val KEY_ERROR_MESSAGE = "error_message"
    }

    override suspend fun doWork(): Result {
        val isInstalledApp = inputData.getBoolean(KEY_IS_INSTALLED_APP, false)
        val installedBaseApkPath = inputData.getString(KEY_INSTALLED_BASE_APK_PATH)
        val installedSplitPaths = inputData.getStringArray(KEY_INSTALLED_SPLIT_PATHS)?.toList() ?: emptyList()
        val externalSourceUriString = inputData.getString(KEY_EXTERNAL_SOURCE_URI)
        val externalSourceUri = externalSourceUriString?.toUri()
        val selectedSplitNames = inputData.getStringArray(KEY_SELECTED_SPLIT_NAMES)?.toSet() ?: emptySet()
        val autoSign = inputData.getBoolean(KEY_AUTO_SIGN, true)
        val bypassSignature = inputData.getBoolean(KEY_BYPASS_SIGNATURE, false)
        val appName = inputData.getString(KEY_APP_NAME) ?: applicationContext.getString(R.string.app_name)
        val packageName = inputData.getString(KEY_PACKAGE_NAME)
        val versionName = inputData.getString(KEY_VERSION_NAME)
        val iconUri = inputData.getString(KEY_ICON_URI)

        NotificationHelper.createNotificationChannel(applicationContext)

        val initialStatus = applicationContext.getString(R.string.status_analyzing_structure, selectedSplitNames.size)
        setForeground(createForegroundInfo(appName, 5, initialStatus))
        setProgress(workDataOf(KEY_PROGRESS to 0.05f, KEY_STATUS_TEXT to initialStatus))

        var extractedSplits: ExtractedSplits? = null

        return try {
            // 1. Prepare and extract splits
            val extracted = splitExtractionHelper.prepareForMerge(
                isInstalledApp = isInstalledApp,
                installedBaseApkPath = installedBaseApkPath,
                installedSplitPaths = installedSplitPaths,
                externalSourceUri = externalSourceUri,
                selectedSplitNames = selectedSplitNames
            )
            extractedSplits = extracted

            val tempMergedFile = File(applicationContext.cacheDir, "merged_worker_${System.currentTimeMillis()}.apk")

            // 2. Perform merge
            val mergeResult = apkMerger.merge(
                baseApkFile = extracted.baseApk,
                splitFiles = extracted.splitApks,
                outputApkFile = tempMergedFile,
                options = MergeOptions(autoSign = autoSign, bypassSignature = bypassSignature),
                onProgress = { progress, statusResId, count ->
                    val text = if (count > 0) applicationContext.getString(statusResId, count) else applicationContext.getString(statusResId)
                    val progressPercent = (progress * 100).toInt()
                    kotlinx.coroutines.runBlocking {
                        setProgress(workDataOf(KEY_PROGRESS to progress, KEY_STATUS_TEXT to text))
                        try {
                            setForeground(createForegroundInfo(appName, progressPercent, text))
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to update foreground notification")
                        }
                    }
                }
            )

            // 3. Clean up staging directory
            splitExtractionHelper.cleanup(extracted)
            extractedSplits = null

            val mergedFile = mergeResult.getOrThrow()

            // 4. Save to Downloads
            val destination = apkOutputManager.saveToDownloads(
                sourceApkFile = mergedFile,
                baseName = packageName ?: appName,
                versionName = versionName
            )
            mergedFile.delete()

            // 5. Record to Room database
            historyRepository.addHistory(
                ConversionHistory(
                    appName = appName,
                    packageName = packageName ?: "",
                    versionName = versionName ?: "",
                    splitCount = selectedSplitNames.size,
                    fileSizeBytes = destination.file.length(),
                    sourceType = if (isInstalledApp) "INSTALLED" else "CONTAINER",
                    outputPath = destination.displayPath,
                    isSuccessful = true
                )
            )

            // 6. Post success notification
            NotificationHelper.showSuccessNotification(applicationContext, appName, destination.file)

            Result.success(
                workDataOf(
                    KEY_OUTPUT_FILE_PATH to destination.file.absolutePath,
                    KEY_DISPLAY_PATH to destination.displayPath,
                    KEY_PROGRESS to 1.0f,
                    KEY_STATUS_TEXT to applicationContext.getString(R.string.status_merge_completed)
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Worker failed to merge APKs")
            extractedSplits?.let { splitExtractionHelper.cleanup(it) }

            val errorMsg = e.message ?: e.localizedMessage ?: "Unknown error"

            // Record failure to Room history
            historyRepository.addHistory(
                ConversionHistory(
                    appName = appName,
                    packageName = packageName ?: "",
                    versionName = versionName ?: "",
                    splitCount = selectedSplitNames.size,
                    fileSizeBytes = 0L,
                    sourceType = if (isInstalledApp) "INSTALLED" else "CONTAINER",
                    outputPath = "",
                    isSuccessful = false,
                    errorMessage = errorMsg
                )
            )

            NotificationHelper.showErrorNotification(applicationContext, appName, errorMsg)

            Result.failure(
                workDataOf(
                    KEY_ERROR_MESSAGE to errorMsg,
                    KEY_STATUS_TEXT to applicationContext.getString(R.string.msg_merge_failed, errorMsg)
                )
            )
        }
    }

    private fun createForegroundInfo(appName: String, progress: Int, statusText: String?): ForegroundInfo {
        val notification = NotificationHelper.buildProgressNotification(
            applicationContext,
            appName,
            progress,
            statusText
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationHelper.NOTIFICATION_ID_PROGRESS, notification)
        }
    }
}
