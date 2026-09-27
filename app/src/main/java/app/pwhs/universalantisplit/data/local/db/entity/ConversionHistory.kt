package app.pwhs.universalantisplit.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_history")
data class ConversionHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val splitCount: Int,
    val fileSizeBytes: Long,
    val sourceType: String, // "INSTALLED" or "CONTAINER"
    val outputPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccessful: Boolean = true,
    val errorMessage: String? = null
)
