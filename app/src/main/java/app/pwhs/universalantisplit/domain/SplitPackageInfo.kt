package app.pwhs.universalantisplit.domain

import android.graphics.Bitmap
import android.net.Uri

data class SplitPackageInfo(
    val name: String,
    val appName: String? = null,
    val packageName: String? = null,
    val versionName: String? = null,
    val sizeFormatted: String,
    val splitNames: List<String>,
    val isPairIpDetected: Boolean = false,
    val sourceUri: Uri? = null,
    val baseApkPath: String? = null,
    val splitPaths: List<String> = emptyList(),
    val iconBitmap: Bitmap? = null,
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val baseApkPath: String,
    val splitPaths: List<String>,
    val isSplitApp: Boolean,
    val sizeBytes: Long,
    val sizeFormatted: String,
)
