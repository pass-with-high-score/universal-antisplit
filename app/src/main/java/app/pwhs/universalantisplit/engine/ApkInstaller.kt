package app.pwhs.universalantisplit.engine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ApkInstaller(private val context: Context) {

    suspend fun install(apkUri: Uri) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)

        installer.openSession(sessionId).use { session ->
            session.openWrite("package", 0, -1).use { out ->
                val input = context.contentResolver.openInputStream(apkUri)
                    ?: throw IOException("Cannot open APK stream: $apkUri")
                input.use { it.copyTo(out) }
                session.fsync(out)
            }
            session.commit(statusPendingIntent(sessionId).intentSender)
        }
    }

    private fun statusPendingIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, ApkInstallReceiver::class.java)
            .setAction(ApkInstallReceiver.ACTION_INSTALL_STATUS)
            .setPackage(context.packageName)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }
}
