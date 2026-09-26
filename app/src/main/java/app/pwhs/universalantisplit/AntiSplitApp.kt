package app.pwhs.universalantisplit

import android.app.Application
import app.pwhs.universalantisplit.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import timber.log.Timber

class AntiSplitApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Plant Timber debug tree in debug builds
        Timber.plant(Timber.DebugTree())

        // Initialize Koin DI
        startKoin {
            androidLogger()
            androidContext(this@AntiSplitApp)
            modules(appModule)
        }
    }
}
