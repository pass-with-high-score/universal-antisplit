package app.pwhs.universalantisplit.engine.merger

import android.content.Context
import app.pwhs.universalantisplit.engine.signer.ApkSignerManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class LienQuanMergeTest {

    @Test
    fun testMergeLienQuan() = runTest {
        val baseDir = File("/Users/macmini0051/AndroidStudioProjects/universal-antisplit/scratch/lienquan")
        val baseApk = File(baseDir, "base.apk")
        if (!baseApk.exists()) {
            println("base.apk does not exist at ${baseApk.absolutePath}, skipping test")
            return@runTest
        }

        val splits = listOf(
            File(baseDir, "split_assetpack.apk"),
            File(baseDir, "split_config.arm64_v8a.apk"),
            File(baseDir, "split_gpdeku.apk"),
            File(baseDir, "split_gpdeku.config.arm64_v8a.apk"),
        )

        val cacheDir = File("/Users/macmini0051/AndroidStudioProjects/universal-antisplit/build/tmp/test_cache").apply { mkdirs() }
        val filesDir = File("/Users/macmini0051/AndroidStudioProjects/universal-antisplit/build/tmp/test_files").apply { mkdirs() }

        val mockContext = mockk<Context>(relaxed = true) {
            every { this@mockk.cacheDir } returns cacheDir
            every { this@mockk.filesDir } returns filesDir
            every { this@mockk.resources.openRawResource(any()) } answers {
                FileInputStream("/Users/macmini0051/AndroidStudioProjects/universal-antisplit/app/src/main/res/raw/antisplit_pms_hook.dex")
            }
        }

        val signerManager = ApkSignerManager(mockContext)
        val merger = ApkMerger(mockContext, signerManager)
        val outputApk = File("/Users/macmini0051/AndroidStudioProjects/universal-antisplit/scratch/lienquan_merged_final.apk")

        val result = merger.merge(
            baseApkFile = baseApk,
            splitFiles = splits,
            outputApkFile = outputApk,
            options = MergeOptions(autoSign = true, bypassSignature = true),
            onProgress = { progress, _, count ->
                println("Progress: ${(progress * 100).toInt()}% (count=$count)")
            }
        )

        println("Result success: ${result.isSuccess}")
        if (result.isFailure) {
            result.exceptionOrNull()?.printStackTrace()
            throw result.exceptionOrNull()!!
        }
        println("Output APK size: ${outputApk.length()} bytes")
    }
}
