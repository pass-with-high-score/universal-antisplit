package app.pwhs.universalantisplit.ui.main

import app.pwhs.universalantisplit.domain.InstalledAppInfo
import java.io.File

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class BatchItemUiState(
    val workId: String,
    val app: InstalledAppInfo,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val progress: Float = 0f,
    val statusText: String = "",
    val outputFile: File? = null,
    val displayPath: String? = null,
    val errorMessage: String? = null
)

data class BatchProgressState(
    val isBatchActive: Boolean = false,
    val isSheetVisible: Boolean = false,
    val items: List<BatchItemUiState> = emptyList(),
    val isCompleted: Boolean = false
) {
    val totalCount: Int get() = items.size
    val completedCount: Int get() = items.count { it.status == BatchItemStatus.COMPLETED }
    val failedCount: Int get() = items.count { it.status == BatchItemStatus.FAILED }
    val isFinished: Boolean
        get() = items.isNotEmpty() && items.all {
            it.status == BatchItemStatus.COMPLETED || it.status == BatchItemStatus.FAILED
        }

    val currentItem: BatchItemUiState?
        get() = items.firstOrNull { it.status == BatchItemStatus.PROCESSING }
            ?: items.firstOrNull { it.status == BatchItemStatus.PENDING }

    val overallProgress: Float
        get() {
            if (items.isEmpty()) return 0f
            val finishedCount = items.count {
                it.status == BatchItemStatus.COMPLETED || it.status == BatchItemStatus.FAILED
            }.toFloat()
            val activeProgress = items.firstOrNull { it.status == BatchItemStatus.PROCESSING }?.progress ?: 0f
            return ((finishedCount + activeProgress) / items.size).coerceIn(0f, 1f)
        }
}
