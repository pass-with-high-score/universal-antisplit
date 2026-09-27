package app.pwhs.universalantisplit.engine.merger

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ApkOutputManager(private val context: Context) {

    data class OutputDestination(
        val file: File,
        val uri: Uri,
        val displayPath: String,
    )

    suspend fun saveToDownloads(
        sourceApkFile: File,
        baseName: String,
        versionName: String? = null,
    ): OutputDestination = withContext(Dispatchers.IO) {
        val safeAppName = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val versionSuffix = if (!versionName.isNullOrBlank()) "_v$versionName" else ""
        val fileName = "${safeAppName}${versionSuffix}_merged.apk"

        // 1. Always keep a primary copy in app external files directory for reliable local access
        val localDir = context.getExternalFilesDir("merged") ?: context.filesDir
        localDir.mkdirs()
        val localFile = File(localDir, fileName)
        if (localFile.exists()) localFile.delete()
        sourceApkFile.copyTo(localFile, overwrite = true)

        val localUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            localFile
        )

        // 2. Export to public Download/UniversalAntiSplit folder
        var publicPath = localFile.absolutePath
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/UniversalAntiSplit"
                    )
                }

                val publicUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (publicUri != null) {
                    resolver.openOutputStream(publicUri)?.use { out ->
                        FileInputStream(sourceApkFile).use { input -> input.copyTo(out) }
                    }
                    publicPath = "/sdcard/${Environment.DIRECTORY_DOWNLOADS}/UniversalAntiSplit/$fileName"
                    Timber.i("Saved to public MediaStore downloads: $publicPath")
                    return@withContext OutputDestination(localFile, publicUri, publicPath)
                }
            } else {
                @Suppress("DEPRECATION")
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "UniversalAntiSplit"
                )
                publicDir.mkdirs()
                val publicFile = File(publicDir, fileName)
                sourceApkFile.copyTo(publicFile, overwrite = true)
                publicPath = publicFile.absolutePath
                Timber.i("Saved to public downloads folder: $publicPath")
                return@withContext OutputDestination(publicFile, localUri, publicPath)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to copy to public downloads, using app-specific path")
        }

        OutputDestination(localFile, localUri, publicPath)
    }
    fun resolveApkFile(outputPath: String): File? {
        if (outputPath.isBlank()) return null
        val direct = File(outputPath)
        if (direct.exists() && direct.isFile) return direct

        // Check in app-specific merged dir
        val localDir = context.getExternalFilesDir("merged") ?: context.filesDir
        val localFile = File(localDir, direct.name)
        if (localFile.exists() && localFile.isFile) return localFile

        // Check in public Downloads directory
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "UniversalAntiSplit"
        )
        val publicFile = File(publicDir, direct.name)
        if (publicFile.exists() && publicFile.isFile) return publicFile

        return null
    }

    fun createInstallIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
