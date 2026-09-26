package app.pwhs.universalantisplit.engine.merger

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class ExtractedSplits(
    val baseApk: File,
    val splitApks: List<File>,
    val stagingDir: File? = null,
)

class SplitExtractionHelper(private val context: Context) {

    suspend fun prepareForMerge(
        isInstalledApp: Boolean,
        installedBaseApkPath: String?,
        installedSplitPaths: List<String>,
        externalSourceUri: Uri?,
        selectedSplitNames: Set<String>,
    ): ExtractedSplits = withContext(Dispatchers.IO) {
        if (isInstalledApp) {
            requireNotNull(installedBaseApkPath) { "Installed base APK path is null" }
            val baseFile = File(installedBaseApkPath)
            val selectedSplits = installedSplitPaths
                .map { File(it) }
                .filter { it.name in selectedSplitNames }

            return@withContext ExtractedSplits(
                baseApk = baseFile,
                splitApks = selectedSplits,
                stagingDir = null
            )
        }

        // External container archive (XAPK, APKM, APKS, ZIP)
        requireNotNull(externalSourceUri) { "External source URI is null" }
        val stagingDir = File(context.cacheDir, "staging_${System.currentTimeMillis()}")
        stagingDir.mkdirs()

        var baseApkFile: File? = null
        val splitApkFiles = mutableListOf<File>()

        context.contentResolver.openInputStream(externalSourceUri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val simpleName = File(name).name

                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val isBase = simpleName.equals("base.apk", ignoreCase = true) ||
                                (baseApkFile == null && (selectedSplitNames.contains(simpleName) || simpleName.contains("base", ignoreCase = true)))

                        if (isBase && baseApkFile == null) {
                            val f = File(stagingDir, simpleName)
                            FileOutputStream(f).use { out -> zis.copyTo(out) }
                            baseApkFile = f
                        } else if (selectedSplitNames.contains(simpleName)) {
                            val f = File(stagingDir, simpleName)
                            FileOutputStream(f).use { out -> zis.copyTo(out) }
                            splitApkFiles.add(f)
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        checkNotNull(baseApkFile) { "Could not find a valid base.apk inside the archive container" }

        Timber.i("Extracted base=${baseApkFile.name}, ${splitApkFiles.size} splits to ${stagingDir.absolutePath}")
        ExtractedSplits(
            baseApk = baseApkFile,
            splitApks = splitApkFiles,
            stagingDir = stagingDir
        )
    }

    fun cleanup(extracted: ExtractedSplits) {
        try {
            extracted.stagingDir?.deleteRecursively()
        } catch (e: Exception) {
            Timber.w(e, "Failed to clean staging directory")
        }
    }
}
