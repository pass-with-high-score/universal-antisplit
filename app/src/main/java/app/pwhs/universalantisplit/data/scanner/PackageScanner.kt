package app.pwhs.universalantisplit.data.scanner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.universalantisplit.domain.InstalledAppInfo
import app.pwhs.universalantisplit.domain.SplitPackageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

interface PackageScanner {
    suspend fun getInstalledApps(includeSystem: Boolean = false): List<InstalledAppInfo>
    suspend fun getAppByPackageName(packageName: String): InstalledAppInfo?
    suspend fun inspectExternalFile(uri: Uri): SplitPackageInfo?
}

class DefaultPackageScanner(private val context: Context) : PackageScanner {

    override suspend fun getAppByPackageName(packageName: String): InstalledAppInfo? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val base = appInfo.sourceDir ?: ""
            val splits = appInfo.splitSourceDirs?.toList() ?: emptyList()
            val isSplit = splits.isNotEmpty()

            var totalSize = if (base.isNotEmpty()) File(base).length() else 0L
            splits.forEach { totalSize += File(it).length() }

            val pkgInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(appInfo.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(appInfo.packageName, 0)
                }
            }.getOrNull()

            InstalledAppInfo(
                appName = appInfo.loadLabel(pm).toString(),
                packageName = appInfo.packageName,
                versionName = pkgInfo?.versionName ?: "",
                baseApkPath = base,
                splitPaths = splits,
                isSplitApp = isSplit,
                sizeBytes = totalSize,
                sizeFormatted = formatFileSize(totalSize),
            )
        }.getOrNull()
    }

    override suspend fun getInstalledApps(includeSystem: Boolean): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        apps.asSequence()
            .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { appInfo ->
                val base = appInfo.sourceDir ?: ""
                val splits = appInfo.splitSourceDirs?.toList() ?: emptyList()
                val isSplit = splits.isNotEmpty()

                var totalSize = if (base.isNotEmpty()) File(base).length() else 0L
                splits.forEach { totalSize += File(it).length() }

                val pkgInfo = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(appInfo.packageName, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(appInfo.packageName, 0)
                    }
                }.getOrNull()

                InstalledAppInfo(
                    appName = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    versionName = pkgInfo?.versionName ?: "",
                    baseApkPath = base,
                    splitPaths = splits,
                    isSplitApp = isSplit,
                    sizeBytes = totalSize,
                    sizeFormatted = formatFileSize(totalSize),
                )
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { it.isSplitApp }
                    .thenBy { it.appName.lowercase() }
            )
            .toList()
    }

    override suspend fun inspectExternalFile(uri: Uri): SplitPackageInfo? = withContext(Dispatchers.IO) {
        var displayName = "selected_package"
        var sizeBytes = 0L

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        val pm = context.packageManager
        val apkEntries = mutableListOf<String>()
        var hasPairIp = false
        var iconBitmap: Bitmap? = null
        var extractedAppName: String? = null
        var extractedPackageName: String? = null
        var extractedVersionName: String? = null

        val lowerName = displayName.lowercase()
        val isSingleApk = lowerName.endsWith(".apk")

        if (isSingleApk) {
            val tempApk = File(context.cacheDir, "temp_inspect_${System.currentTimeMillis()}.apk")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempApk).use { output -> input.copyTo(output) }
                }

                if (tempApk.exists() && tempApk.length() > 0) {
                    val apkMetadata = extractApkMetadata(tempApk, pm)
                    extractedAppName = apkMetadata.appName
                    extractedPackageName = apkMetadata.packageName
                    extractedVersionName = apkMetadata.versionName
                    iconBitmap = apkMetadata.iconBitmap
                    apkEntries.add(displayName)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                tempApk.delete()
            }
        } else {
            // Container archive: XAPK, APKM, APKS, ZIP
            var tempBaseApk: File? = null
            var iconBytes: ByteArray? = null

            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ZipInputStream(stream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            val simpleName = File(name).name

                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkEntries.add(simpleName)

                                val isBaseCandidate = simpleName.equals("base.apk", ignoreCase = true) ||
                                        simpleName.contains("base", ignoreCase = true) ||
                                        tempBaseApk == null

                                if (isBaseCandidate && (tempBaseApk == null || simpleName.equals("base.apk", ignoreCase = true))) {
                                    tempBaseApk?.delete()
                                    val f = File(context.cacheDir, "temp_cont_base_${System.currentTimeMillis()}.apk")
                                    FileOutputStream(f).use { output -> zis.copyTo(output) }
                                    tempBaseApk = f
                                }
                            }

                            if (name.contains("pairip", ignoreCase = true)) {
                                hasPairIp = true
                            }

                            if (iconBytes == null && (name.equals("icon.png", ignoreCase = true) || name.endsWith("/icon.png", ignoreCase = true))) {
                                runCatching { iconBytes = zis.readBytes() }
                            }

                            if (name.equals("manifest.json", ignoreCase = true)) {
                                runCatching {
                                    val jsonStr = zis.bufferedReader().readText()
                                    val json = JSONObject(jsonStr)
                                    if (json.has("name")) extractedAppName = json.optString("name")
                                    if (json.has("package_name")) extractedPackageName = json.optString("package_name")
                                    if (json.has("version_name")) extractedVersionName = json.optString("version_name")
                                }
                            } else if (name.equals("info.json", ignoreCase = true)) {
                                runCatching {
                                    val jsonStr = zis.bufferedReader().readText()
                                    val json = JSONObject(jsonStr)
                                    if (json.has("app_name")) extractedAppName = json.optString("app_name")
                                    if (json.has("pname")) extractedPackageName = json.optString("pname")
                                    if (json.has("release_version")) extractedVersionName = json.optString("release_version")
                                    else if (json.has("version_name")) extractedVersionName = json.optString("version_name")
                                }
                            }

                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                if (iconBytes != null) {
                    runCatching {
                        iconBitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
                    }
                }

                tempBaseApk?.let { baseFile ->
                    if (baseFile.exists() && baseFile.length() > 0) {
                        val baseMeta = extractApkMetadata(baseFile, pm)
                        if (extractedAppName.isNullOrBlank()) extractedAppName = baseMeta.appName
                        if (extractedPackageName.isNullOrBlank()) extractedPackageName = baseMeta.packageName
                        if (extractedVersionName.isNullOrBlank()) extractedVersionName = baseMeta.versionName
                        if (iconBitmap == null) iconBitmap = baseMeta.iconBitmap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                tempBaseApk?.delete()
            }
        }

        val cleanDisplayName = displayName.substringBeforeLast(".")
        val resolvedAppName = extractedAppName?.takeIf { it.isNotBlank() } ?: cleanDisplayName

        SplitPackageInfo(
            name = displayName,
            appName = resolvedAppName,
            packageName = extractedPackageName,
            versionName = extractedVersionName,
            sizeFormatted = formatFileSize(sizeBytes),
            splitNames = apkEntries,
            isPairIpDetected = hasPairIp,
            sourceUri = uri,
            iconBitmap = iconBitmap
        )
    }

    private data class ApkMetadata(
        val appName: String?,
        val packageName: String?,
        val versionName: String?,
        val iconBitmap: Bitmap?
    )

    private fun extractApkMetadata(apkFile: File, pm: PackageManager): ApkMetadata {
        var appName: String? = null
        var packageName: String? = null
        var versionName: String? = null
        var iconBitmap: Bitmap? = null

        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
        val pi = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }
        }.getOrNull()

        if (pi != null) {
            packageName = pi.packageName
            versionName = pi.versionName

            val appInfo = pi.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath

                appName = runCatching {
                    if (appInfo.labelRes != 0) {
                        val res = pm.getResourcesForApplication(appInfo)
                        res.getString(appInfo.labelRes)
                    } else null
                }.getOrNull()

                if (appName.isNullOrBlank()) {
                    appName = runCatching {
                        appInfo.loadLabel(pm).toString().takeIf { it != packageName }
                    }.getOrNull()
                }
                if (appName.isNullOrBlank()) {
                    appName = appInfo.nonLocalizedLabel?.toString()
                }

                iconBitmap = runCatching {
                    if (appInfo.icon != 0) {
                        val res = pm.getResourcesForApplication(appInfo)
                        val drawable = res.getDrawable(appInfo.icon, null)
                        drawable.toBitmap(144, 144)
                    } else null
                }.getOrNull()

                if (iconBitmap == null) {
                    iconBitmap = runCatching {
                        appInfo.loadIcon(pm).toBitmap(144, 144)
                    }.getOrNull()
                }
            }
        }

        return ApkMetadata(appName, packageName, versionName, iconBitmap)
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format("%.1f %s", value, units[index])
    }
}
