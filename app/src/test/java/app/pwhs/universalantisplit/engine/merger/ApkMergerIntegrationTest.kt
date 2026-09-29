package app.pwhs.universalantisplit.engine.merger

import android.content.Context
import app.pwhs.universalantisplit.engine.signer.ApkSignerManager
import com.android.apksig.ApkVerifier
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkMergerIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testMergeAndSignPipeline() = runTest {
        val rootDir = tempFolder.newFolder("apk_test")
        val cacheDir = File(rootDir, "cache").apply { mkdirs() }
        val filesDir = File(rootDir, "files").apply { mkdirs() }

        val mockContext = mockk<Context>(relaxed = true) {
            every { this@mockk.cacheDir } returns cacheDir
            every { this@mockk.filesDir } returns filesDir
        }

        // 1. Create a dummy base.apk
        val baseApk = File(rootDir, "base.apk")
        val realDebugApk = File("build/outputs/apk/debug/app-debug.apk")
        val manifestContent = if (realDebugApk.exists()) {
            ZipFile(realDebugApk).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml")
                zip.getInputStream(entry).readBytes()
            }
        } else {
            // Minimal valid AXML chunk (RES_XML_TYPE = 0x0003, header size = 8, total size = 8)
            byteArrayOf(0x03, 0x00, 0x08, 0x00, 0x08, 0x00, 0x00, 0x00)
        }

        ZipOutputStream(FileOutputStream(baseApk)).use { zos ->
            // AndroidManifest.xml with real binary AXML
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(manifestContent)
            zos.closeEntry()

            // resources.arsc (originally deflated, must be converted to STORED and aligned)
            val arscContent = "ARSC_HEADER_RESOURCES_TABLE_DUMMY_DATA_12345678".toByteArray()
            val arscEntry = ZipEntry("resources.arsc")
            zos.putNextEntry(arscEntry)
            zos.write(arscContent)
            zos.closeEntry()

            // classes.dex
            val dexContent = "DEX_BASE_035".toByteArray(StandardCharsets.US_ASCII)
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(dexContent)
            zos.closeEntry()

            // META-INF/MANIFEST.MF to be stripped
            zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zos.write("Manifest-Version: 1.0".toByteArray())
            zos.closeEntry()
        }

        // 2. Create a dummy split_config.arm64_v8a.apk
        val splitApk = File(rootDir, "split_config.arm64_v8a.apk")
        ZipOutputStream(FileOutputStream(splitApk)).use { zos ->
            // Another classes.dex (must be renumbered to classes2.dex)
            val splitDexContent = "DEX_SPLIT_035".toByteArray(StandardCharsets.US_ASCII)
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(splitDexContent)
            zos.closeEntry()

            // Native library
            val soContent = "ELF_ARM64_SO".toByteArray(StandardCharsets.US_ASCII)
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libnative.so"))
            zos.write(soContent)
            zos.closeEntry()
        }

        // 3. Run Merger with signing
        val signerManager = ApkSignerManager(mockContext)
        val merger = ApkMerger(mockContext, signerManager)
        val outputApk = File(rootDir, "merged_signed.apk")

        val result = merger.merge(
            baseApkFile = baseApk,
            splitFiles = listOf(splitApk),
            outputApkFile = outputApk,
            options = MergeOptions(autoSign = true)
        )

        assertTrue("Merge pipeline failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val signedFile = result.getOrThrow()
        assertTrue("Output file does not exist", signedFile.exists())
        assertTrue("Output file size must be > 0", signedFile.length() > 0)

        // 4. Verify contents of merged APK
        ZipFile(signedFile).use { zip ->
            // AndroidManifest.xml must exist
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
            assertNotNull("AndroidManifest.xml missing", manifestEntry)
            val manifestBytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
            assertTrue("Sanitized manifest must not be empty", manifestBytes.isNotEmpty())

            // classes.dex (from base) must exist
            val dex1 = zip.getEntry("classes.dex")
            assertNotNull("classes.dex missing", dex1)
            val dex1Content = String(zip.getInputStream(dex1).use { it.readBytes() }, StandardCharsets.US_ASCII)
            assertEquals("DEX_BASE_035", dex1Content)

            // classes2.dex (renumbered from split) must exist!
            val dex2 = zip.getEntry("classes2.dex")
            assertNotNull("classes2.dex missing (DEX renumbering failed!)", dex2)
            val dex2Content = String(zip.getInputStream(dex2).use { it.readBytes() }, StandardCharsets.US_ASCII)
            assertEquals("DEX_SPLIT_035", dex2Content)

            // resources.arsc must exist and MUST BE STORED (uncompressed) for Android R+
            val arscEntry = zip.getEntry("resources.arsc")
            assertNotNull("resources.arsc missing", arscEntry)
            assertEquals("resources.arsc MUST be STORED (method=0)", ZipEntry.STORED, arscEntry.method)

            // Native lib from split must exist and MUST BE STORED for Android 15 page alignment
            val nativeEntry = zip.getEntry("lib/arm64-v8a/libnative.so")
            assertNotNull("lib/arm64-v8a/libnative.so missing", nativeEntry)
            assertEquals("Native .so file MUST be STORED (method=0)", ZipEntry.STORED, nativeEntry.method)
        }

        // 5. Verify cryptographic signatures using Google ApkVerifier (checking against minSdk 21)
        val verifier = ApkVerifier.Builder(signedFile)
            .setMinCheckedPlatformVersion(21)
            .build()
        val verifyResult = verifier.verify()
        assertTrue("APK signature verification failed: ${verifyResult.errors}", verifyResult.isVerified)
        assertTrue("V2 or V3 scheme must be verified", verifyResult.isVerifiedUsingV2Scheme || verifyResult.isVerifiedUsingV3Scheme)
    }
}
