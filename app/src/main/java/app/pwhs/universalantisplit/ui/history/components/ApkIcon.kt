package app.pwhs.universalantisplit.ui.history.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun rememberApkIcon(packageName: String, outputPath: String): State<Bitmap?> {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, key1 = packageName, key2 = outputPath) {
        value = withContext(Dispatchers.IO) {
            val fromInstalled = runCatching {
                if (packageName.isNotBlank()) {
                    val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
                    drawable.toBitmap(width = 96, height = 96)
                } else null
            }.getOrNull()

            fromInstalled ?: runCatching {
                if (outputPath.isNotBlank()) {
                    val direct = File(outputPath)
                    val file = if (direct.exists()) direct else {
                        val localDir = context.getExternalFilesDir("merged") ?: context.filesDir
                        File(localDir, direct.name).takeIf { it.exists() }
                    }
                    if (file != null && file.exists()) {
                        val pm = context.packageManager
                        val pi = pm.getPackageArchiveInfo(file.absolutePath, 0)
                        val appInfo = pi?.applicationInfo
                        if (appInfo != null) {
                            appInfo.sourceDir = file.absolutePath
                            appInfo.publicSourceDir = file.absolutePath
                            appInfo.loadIcon(pm).toBitmap(width = 96, height = 96)
                        } else null
                    } else null
                } else null
            }.getOrNull()
        }
    }
}
