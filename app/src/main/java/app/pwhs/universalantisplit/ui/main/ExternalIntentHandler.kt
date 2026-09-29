package app.pwhs.universalantisplit.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.data.scanner.PackageScanner
import app.pwhs.universalantisplit.domain.InstalledAppInfo
import app.pwhs.universalantisplit.domain.SplitPackageInfo
import app.pwhs.universalantisplit.protocol.UniversalInstallerProtocol
import timber.log.Timber

sealed class IntentProcessResult {
    data class TargetAppFound(val app: InstalledAppInfo, val autoStart: Boolean) : IntentProcessResult()
    data class ExternalFileLoaded(val info: SplitPackageInfo, val autoStart: Boolean) : IntentProcessResult()
    data class Message(val message: String) : IntentProcessResult()
}

class ExternalIntentHandler(
    private val context: Context,
    private val packageScanner: PackageScanner,
) {
    suspend fun processIntent(
        intent: Intent?,
        onAutoSignOverride: ((Boolean) -> Unit)? = null,
    ): IntentProcessResult? {
        if (intent == null) return null
        val action = intent.action ?: return null
        Timber.i("Processing incoming intent: action=$action, data=${intent.data}")

        if (intent.hasExtra(UniversalInstallerProtocol.EXTRA_AUTO_SIGN)) {
            val autoSign = intent.getBooleanExtra(UniversalInstallerProtocol.EXTRA_AUTO_SIGN, true)
            onAutoSignOverride?.invoke(autoSign)
        }

        val autoStart = intent.getBooleanExtra(UniversalInstallerProtocol.EXTRA_AUTO_START, false)

        val targetPackage = intent.getStringExtra(UniversalInstallerProtocol.EXTRA_PACKAGE_NAME)
        if (!targetPackage.isNullOrBlank()) {
            val matched = packageScanner.getAppByPackageName(targetPackage)
            return if (matched != null) {
                IntentProcessResult.TargetAppFound(matched, autoStart)
            } else {
                IntentProcessResult.Message(context.getString(R.string.msg_package_not_found, targetPackage))
            }
        }

        val uri = intent.data
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: if (action == Intent.ACTION_SEND) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            } else null

        if (uri != null) {
            val info = packageScanner.inspectExternalFile(uri)
            return if (info != null) {
                IntentProcessResult.ExternalFileLoaded(info, autoStart)
            } else {
                IntentProcessResult.Message(context.getString(R.string.msg_cannot_read_file))
            }
        }

        return null
    }
}
