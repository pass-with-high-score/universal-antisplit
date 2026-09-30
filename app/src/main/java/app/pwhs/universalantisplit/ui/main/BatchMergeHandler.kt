package app.pwhs.universalantisplit.ui.main

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.domain.InstalledAppInfo
import app.pwhs.universalantisplit.worker.ApkMergerWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class BatchMergeHandler(
    private val context: Context,
    private val workManager: WorkManager,
    private val scope: CoroutineScope,
    private val uiStateFlow: MutableStateFlow<MainUiState>,
    private val sendEvent: suspend (MainEvent) -> Unit,
) {
    companion object {
        const val BATCH_WORK_NAME = "apk_batch_merge_work"
        const val TAG_BATCH = "batch_merge"
    }

    private var observerJob: Job? = null

    fun startBatchMerge(
        apps: List<InstalledAppInfo>,
        autoSign: Boolean,
        bypassSignature: Boolean
    ) {
        if (apps.isEmpty()) return

        observerJob?.cancel()

        val requests = apps.map { app ->
            val inputData = workDataOf(
                ApkMergerWorker.KEY_IS_INSTALLED_APP to true,
                ApkMergerWorker.KEY_INSTALLED_BASE_APK_PATH to app.baseApkPath,
                ApkMergerWorker.KEY_INSTALLED_SPLIT_PATHS to app.splitPaths.toTypedArray(),
                ApkMergerWorker.KEY_SELECTED_SPLIT_NAMES to app.splitPaths.map { File(it).name }.toTypedArray(),
                ApkMergerWorker.KEY_AUTO_SIGN to autoSign,
                ApkMergerWorker.KEY_BYPASS_SIGNATURE to bypassSignature,
                ApkMergerWorker.KEY_APP_NAME to app.appName,
                ApkMergerWorker.KEY_PACKAGE_NAME to app.packageName,
                ApkMergerWorker.KEY_VERSION_NAME to app.versionName
            )
            OneTimeWorkRequestBuilder<ApkMergerWorker>()
                .setInputData(inputData)
                .addTag("apk_merge")
                .addTag(TAG_BATCH)
                .build()
        }

        val initialItems = apps.mapIndexed { index, app ->
            BatchItemUiState(
                workId = requests[index].id.toString(),
                app = app,
                status = if (index == 0) BatchItemStatus.PROCESSING else BatchItemStatus.PENDING,
                statusText = if (index == 0) context.getString(R.string.status_analyzing_structure, app.splitPaths.size) else context.getString(R.string.batch_status_pending)
            )
        }

        val initialState = BatchProgressState(
            isBatchActive = true,
            isSheetVisible = true,
            items = initialItems,
            isCompleted = false
        )

        uiStateFlow.update { it.copy(batchState = initialState) }

        var continuation = workManager.beginUniqueWork(
            BATCH_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            requests.first()
        )
        for (i in 1 until requests.size) {
            continuation = continuation.then(requests[i])
        }
        continuation.enqueue()

        observeBatchWork()
    }

    private fun observeBatchWork() {
        observerJob = scope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(BATCH_WORK_NAME).collect { workInfos ->
                val currentBatch = uiStateFlow.value.batchState
                if (!currentBatch.isBatchActive || currentBatch.items.isEmpty()) return@collect

                val workMap = workInfos.associateBy { it.id.toString() }

                val updatedItems = currentBatch.items.map { item ->
                    val workInfo = workMap[item.workId] ?: return@map item
                    when (workInfo.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                            item.copy(
                                status = BatchItemStatus.PENDING,
                                statusText = context.getString(R.string.batch_status_pending)
                            )
                        }
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getFloat(ApkMergerWorker.KEY_PROGRESS, 0.05f)
                            val statusText = workInfo.progress.getString(ApkMergerWorker.KEY_STATUS_TEXT)
                                ?: context.getString(R.string.batch_status_processing)
                            item.copy(
                                status = BatchItemStatus.PROCESSING,
                                progress = progress,
                                statusText = statusText
                            )
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val outPath = workInfo.outputData.getString(ApkMergerWorker.KEY_OUTPUT_FILE_PATH)
                            val displayPath = workInfo.outputData.getString(ApkMergerWorker.KEY_DISPLAY_PATH) ?: outPath
                            item.copy(
                                status = BatchItemStatus.COMPLETED,
                                progress = 1.0f,
                                statusText = context.getString(R.string.batch_status_completed),
                                outputFile = outPath?.let { File(it) },
                                displayPath = displayPath
                            )
                        }
                        WorkInfo.State.FAILED -> {
                            val errMsg = workInfo.outputData.getString(ApkMergerWorker.KEY_ERROR_MESSAGE)
                                ?: context.getString(R.string.msg_merge_failed, "Unknown error")
                            item.copy(
                                status = BatchItemStatus.FAILED,
                                progress = 0f,
                                statusText = context.getString(R.string.batch_status_failed),
                                errorMessage = errMsg
                            )
                        }
                        WorkInfo.State.CANCELLED -> {
                            item.copy(
                                status = BatchItemStatus.FAILED,
                                progress = 0f,
                                statusText = context.getString(R.string.batch_status_failed),
                                errorMessage = "Cancelled"
                            )
                        }
                    }
                }

                val allDone = updatedItems.isNotEmpty() && updatedItems.all {
                    it.status == BatchItemStatus.COMPLETED || it.status == BatchItemStatus.FAILED
                }

                val newState = currentBatch.copy(
                    items = updatedItems,
                    isCompleted = allDone,
                    isSheetVisible = if (allDone) false else currentBatch.isSheetVisible
                )

                uiStateFlow.update { it.copy(batchState = newState) }

                if (allDone) {
                    sendEvent(MainEvent.BatchMergeCompleted(newState))
                    observerJob?.cancel()
                }
            }
        }
    }

    fun setSheetVisible(visible: Boolean) {
        uiStateFlow.update {
            it.copy(batchState = it.batchState.copy(isSheetVisible = visible))
        }
    }

    fun cancelBatch() {
        workManager.cancelUniqueWork(BATCH_WORK_NAME)
        observerJob?.cancel()
        uiStateFlow.update {
            it.copy(batchState = BatchProgressState())
        }
    }

    fun dismissBatch() {
        uiStateFlow.update {
            it.copy(batchState = BatchProgressState())
        }
    }
}
