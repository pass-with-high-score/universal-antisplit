package app.pwhs.universalantisplit.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.pwhs.universalantisplit.data.local.db.dao.ConversionHistoryDao
import app.pwhs.universalantisplit.data.local.db.entity.ConversionHistory

@Database(
    entities = [ConversionHistory::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversionHistoryDao(): ConversionHistoryDao
}
