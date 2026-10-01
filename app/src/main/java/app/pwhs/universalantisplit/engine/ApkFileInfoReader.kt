package app.pwhs.universalantisplit.engine

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import app.pwhs.universalantisplit.domain.ApkFileInfo
import java.io.File
import java.util.zip.ZipFile

class ApkFileInfoReader(private val context: Context) {

    fun read(file: File): ApkFileInfo? {
        if (!file.exists()) return null
        val pm = context.packageManager
        val pi = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val appInfo = pi.applicationInfo
        val abis = runCatching {
            ZipFile(file).use { zip -> abisFromEntryNames(zip.entries().asSequence().map { it.name }.toList()) }
        }.getOrDefault(emptyList())
        return ApkFileInfo(
            versionName = pi.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(pi),
            minSdk = appInfo?.minSdkVersion ?: 0,
            targetSdk = appInfo?.targetSdkVersion ?: 0,
            abis = abis,
        )
    }

    companion object {
        fun abisFromEntryNames(entryNames: List<String>): List<String> =
            entryNames.asSequence()
                .mapNotNull { name ->
                    val match = Regex("^lib/([^/]+)/[^/]+\\.so$").find(name)
                    match?.groupValues?.get(1)
                }
                .distinct()
                .sorted()
                .toList()
    }
}
