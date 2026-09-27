package app.pwhs.universalantisplit.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import app.pwhs.universalantisplit.ui.main.MainScreenViewModel
import app.pwhs.universalantisplit.ui.settings.SettingsScreenViewModel
import app.pwhs.universalantisplit.data.DataRepository
import app.pwhs.universalantisplit.data.DefaultDataRepository

val appModule = module {
    // Process-scoped CoroutineScope for long-running split merging jobs
    single<CoroutineScope>(qualifier = org.koin.core.qualifier.named("appScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single<DataRepository> { DefaultDataRepository() }
    single<app.pwhs.universalantisplit.data.scanner.PackageScanner> { app.pwhs.universalantisplit.data.scanner.DefaultPackageScanner(get()) }
    single { app.pwhs.universalantisplit.engine.signer.ApkSignerManager(get()) }
    single { app.pwhs.universalantisplit.engine.merger.ApkMerger(get(), get()) }
    single { app.pwhs.universalantisplit.engine.merger.ApkOutputManager(get()) }
    single { app.pwhs.universalantisplit.engine.merger.SplitExtractionHelper(get()) }

    viewModelOf(::MainScreenViewModel)
    viewModelOf(::SettingsScreenViewModel)
}
