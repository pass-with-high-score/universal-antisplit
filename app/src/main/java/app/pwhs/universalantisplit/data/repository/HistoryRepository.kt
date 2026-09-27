package app.pwhs.universalantisplit.data.repository

import app.pwhs.universalantisplit.data.local.db.dao.ConversionHistoryDao
import app.pwhs.universalantisplit.data.local.db.entity.ConversionHistory
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<ConversionHistory>>
    suspend fun getHistoryById(id: Long): ConversionHistory?
    suspend fun addHistory(history: ConversionHistory): Long
    suspend fun deleteHistory(history: ConversionHistory)
    suspend fun deleteHistoryById(id: Long)
    suspend fun clearAllHistory()
}

class DefaultHistoryRepository(
    private val dao: ConversionHistoryDao
) : HistoryRepository {
    override fun getAllHistory(): Flow<List<ConversionHistory>> = dao.getAll()

    override suspend fun getHistoryById(id: Long): ConversionHistory? = dao.getById(id)

    override suspend fun addHistory(history: ConversionHistory): Long = dao.insert(history)

    override suspend fun deleteHistory(history: ConversionHistory) = dao.delete(history)

    override suspend fun deleteHistoryById(id: Long) = dao.deleteById(id)

    override suspend fun clearAllHistory() = dao.clearAll()
}
