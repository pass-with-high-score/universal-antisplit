package app.pwhs.universalantisplit.data.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.DecimalFormat

class AppCacheManager(private val context: Context) {

    data class CacheBreakdown(
        val stagingSize: Long,
        val tempApkSize: Long,
        val localMergedSize: Long,
        val totalSize: Long,
        val formattedTotalSize: String
    )

    suspend fun getCacheBreakdown(): CacheBreakdown = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val externalFilesDir = context.getExternalFilesDir("merged")

        var stagingSize = 0L
        var tempApkSize = 0L

        cacheDir?.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name.startsWith("staging_")) {
                stagingSize += calculateDirectorySize(file)
            } else if (file.isFile && (file.name.endsWith(".apk") || file.name.endsWith(".tmp"))) {
                tempApkSize += file.length()
            }
        }

        val localMergedSize = externalFilesDir?.let { calculateDirectorySize(it) } ?: 0L
        val totalSize = stagingSize + tempApkSize + localMergedSize

        CacheBreakdown(
            stagingSize = stagingSize,
            tempApkSize = tempApkSize,
            localMergedSize = localMergedSize,
            totalSize = totalSize,
            formattedTotalSize = formatBytes(totalSize)
        )
    }

    suspend fun clearCache(includeLocalMerged: Boolean = false): Long = withContext(Dispatchers.IO) {
        var bytesFreed = 0L
        val cacheDir = context.cacheDir

        cacheDir?.listFiles()?.forEach { file ->
            try {
                if (file.isDirectory && file.name.startsWith("staging_")) {
                    val size = calculateDirectorySize(file)
                    if (file.deleteRecursively()) {
                        bytesFreed += size
                    }
                } else if (file.isFile && (file.name.endsWith(".apk") || file.name.endsWith(".tmp"))) {
                    val size = file.length()
                    if (file.delete()) {
                        bytesFreed += size
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to clean temporary cache file: ${file.name}")
            }
        }

        if (includeLocalMerged) {
            context.getExternalFilesDir("merged")?.listFiles()?.forEach { file ->
                try {
                    val size = if (file.isDirectory) calculateDirectorySize(file) else file.length()
                    if (if (file.isDirectory) file.deleteRecursively() else file.delete()) {
                        bytesFreed += size
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to clean local merged file: ${file.name}")
                }
            }
        }

        Timber.i("Cache cleared. Freed $bytesFreed bytes.")
        bytesFreed
    }

    private fun calculateDirectorySize(directory: File): Long {
        var length = 0L
        val files = directory.listFiles() ?: return 0L
        for (file in files) {
            length += if (file.isFile) {
                file.length()
            } else {
                calculateDirectorySize(file)
            }
        }
        return length
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[index]
    }
}
