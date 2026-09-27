package app.pwhs.universalantisplit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pwhs.universalantisplit.protocol.UniversalInstallerProtocol
import app.pwhs.universalantisplit.ui.base.BaseActivity
import app.pwhs.universalantisplit.ui.main.MainEvent
import app.pwhs.universalantisplit.ui.main.MainScreenViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class MainActivity : BaseActivity() {

    private val mainViewModel: MainScreenViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app.pwhs.universalantisplit.worker.NotificationHelper.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()
        handleIncomingIntent(intent)
        observeViewModelEvents()

        setContentWithTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MainNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent != null && intent.action != null && intent.action != Intent.ACTION_MAIN) {
            mainViewModel.handleIncomingIntent(intent)
        }
    }

    private fun observeViewModelEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.events.collect { event ->
                    if (event is MainEvent.MergeCompleted) {
                        val currentIntent = intent
                        val isPluginCall = callingActivity != null ||
                                currentIntent?.action == UniversalInstallerProtocol.ACTION_MERGE_SPLIT

                        if (isPluginCall) {
                            val resultData = Intent().apply {
                                data = event.outputUri
                                putExtra(UniversalInstallerProtocol.EXTRA_OUTPUT_PATH, event.outputFile.absolutePath)
                                putExtra(UniversalInstallerProtocol.EXTRA_OUTPUT_URI, event.outputUri.toString())
                                putExtra(UniversalInstallerProtocol.EXTRA_RESULT_PACKAGE, mainViewModel.uiState.value.selectedPackageName)
                                putExtra(UniversalInstallerProtocol.EXTRA_SUCCESS, true)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            setResult(Activity.RESULT_OK, resultData)
                            Timber.i("Returned RESULT_OK to calling activity with merged APK: ${event.outputFile.absolutePath}")
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}

