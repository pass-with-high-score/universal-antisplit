package app.pwhs.universalantisplit.ui.history

sealed interface HistoryEvent {
    data class InstallFailed(val message: String?) : HistoryEvent
    data object FileUnavailable : HistoryEvent
}
