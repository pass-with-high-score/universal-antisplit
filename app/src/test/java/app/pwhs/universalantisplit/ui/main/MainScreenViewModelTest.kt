package app.pwhs.universalantisplit.ui.main

import android.content.Context
import android.net.Uri
import app.pwhs.universalantisplit.data.DataRepository
import app.pwhs.universalantisplit.data.scanner.PackageScanner
import app.pwhs.universalantisplit.domain.InstalledAppInfo
import app.pwhs.universalantisplit.domain.SplitPackageInfo
import app.pwhs.universalantisplit.engine.merger.ApkMerger
import app.pwhs.universalantisplit.engine.merger.ApkOutputManager
import app.pwhs.universalantisplit.engine.merger.SplitExtractionHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MainScreenViewModelTest {

    private lateinit var mockContext: Context
    private lateinit var mockMerger: ApkMerger
    private lateinit var mockOutputManager: ApkOutputManager
    private lateinit var mockExtractionHelper: SplitExtractionHelper

    private lateinit var mockHistoryRepository: app.pwhs.universalantisplit.data.repository.HistoryRepository
    private lateinit var mockWorkManager: androidx.work.WorkManager
    private lateinit var mockCacheManager: app.pwhs.universalantisplit.data.cache.AppCacheManager

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true) {
            every { getString(any()) } returns "mock_string"
            every { getString(any(), *anyVararg()) } returns "mock_formatted_string"
        }
        mockMerger = mockk(relaxed = true)
        mockOutputManager = mockk(relaxed = true)
        mockExtractionHelper = mockk(relaxed = true)
        mockHistoryRepository = mockk(relaxed = true)
        mockCacheManager = mockk(relaxed = true) {
            coEvery { getCacheBreakdown() } returns app.pwhs.universalantisplit.data.cache.AppCacheManager.CacheBreakdown(
                stagingSize = 0L,
                tempApkSize = 0L,
                localMergedSize = 0L,
                totalSize = 0L,
                formattedTotalSize = "0 B"
            )
        }
        mockWorkManager = mockk(relaxed = true) {
            every { getWorkInfosForUniqueWorkFlow(any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }

    private fun createViewModel(): MainScreenViewModel {
        return MainScreenViewModel(
            context = mockContext,
            dataRepository = FakeRepository(),
            packageScanner = FakeScanner(),
            apkMerger = mockMerger,
            apkOutputManager = mockOutputManager,
            splitExtractionHelper = mockExtractionHelper,
            historyRepository = mockHistoryRepository,
            workManager = mockWorkManager,
            appCacheManager = mockCacheManager,
            apkInstaller = mockk(relaxed = true),
        )
    }

    @Test
    fun uiState_initiallyEmpty() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.uiState.first()
        assertEquals(null, state.selectedAppName)
        assertTrue(state.autoSignMergedApk)
    }

    @Test
    fun uiState_onInstalledAppSelected_updatesState() = runTest {
        val viewModel = createViewModel()
        val app = InstalledAppInfo(
            appName = "Demo App",
            packageName = "com.demo.app",
            versionName = "1.0",
            baseApkPath = "/data/app/base.apk",
            splitPaths = listOf("/data/app/split_config.arm64_v8a.apk"),
            isSplitApp = true,
            sizeBytes = 1024L,
            sizeFormatted = "1.0 KB"
        )
        viewModel.onInstalledAppSelected(app)
        val state = viewModel.uiState.first()
        assertEquals("Demo App", state.selectedAppName)
        assertEquals("com.demo.app", state.selectedPackageName)
        assertEquals(2, state.splitCount)
        assertEquals(2, state.selectedSplitItems.size)

        // Test toggle split item (cannot toggle base.apk)
        viewModel.onToggleSplitItem("base.apk (Main)")
        assertTrue(viewModel.uiState.first().selectedSplitItems.contains("base.apk (Main)"))

        // Can toggle secondary split
        viewModel.onToggleSplitItem("split_config.arm64_v8a.apk")
        assertEquals(1, viewModel.uiState.first().selectedSplitItems.size)

        // Select all splits
        viewModel.onSelectAllSplits(true)
        assertEquals(2, viewModel.uiState.first().selectedSplitItems.size)
    }
}

private class FakeRepository : DataRepository {
    override val data: Flow<List<String>> = flow { emit(listOf("Sample")) }
}

private class FakeScanner : PackageScanner {
    override suspend fun getInstalledApps(includeSystem: Boolean): List<InstalledAppInfo> = emptyList()
    override suspend fun inspectExternalFile(uri: Uri): SplitPackageInfo? = null
    override suspend fun getAppByPackageName(packageName: String): InstalledAppInfo? = null
}
