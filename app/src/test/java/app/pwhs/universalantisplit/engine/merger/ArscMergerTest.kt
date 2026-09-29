package app.pwhs.universalantisplit.engine.merger

import com.reandroid.arsc.chunk.TableBlock
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

class ArscMergerTest {

    @Test
    fun testMergeShopeeArsc() {
        val baseDir = File("/Users/nqmgaming/.gemini/antigravity-ide/brain/dc52b54a-e291-4402-8480-605d2a955fa1/scratch/shopee_apks")
        val baseApk = File(baseDir, "base.apk")
        val splitXxhdpi = File(baseDir, "split_config.xxhdpi.apk")

        if (!baseApk.exists() || !splitXxhdpi.exists()) {
            println("Shopee APKs not found, skipping test")
            return
        }

        val baseArscBytes = ZipFile(baseApk).use { zip ->
            val entry = zip.getEntry("resources.arsc")
            zip.getInputStream(entry).readBytes()
        }

        val splitArscBytes = ZipFile(splitXxhdpi).use { zip ->
            val entry = zip.getEntry("resources.arsc")
            zip.getInputStream(entry).readBytes()
        }

        val baseTable = TableBlock()
        baseTable.readBytes(ByteArrayInputStream(baseArscBytes))

        val splitTable = TableBlock()
        splitTable.readBytes(ByteArrayInputStream(splitArscBytes))


        baseTable.merge(splitTable)
        baseTable.refresh()

        val mergedBytes = baseTable.bytes
        println("Merged ARSC size: ${mergedBytes.size} bytes (base: ${baseArscBytes.size}, split: ${splitArscBytes.size})")
        assertTrue(mergedBytes.size > baseArscBytes.size)

        // Verify with another TableBlock reading back the merged bytes
        val verifyTable = TableBlock()
        verifyTable.readBytes(ByteArrayInputStream(mergedBytes))
        
        // Check resource 0x7f080399 (launcher background)
        val res399 = verifyTable.getResource(0x7f080399)
        println("Found 0x7f080399: ${res399?.name}")
        assertNotNull("0x7f080399 should exist after merge", res399)

        // Check resource 0x7f08039a (launcher foreground)
        val res39a = verifyTable.getResource(0x7f08039a)
        println("Found 0x7f08039a: ${res39a?.name}")
        assertNotNull("0x7f08039a should exist after merge", res39a)

        // Check resource 0x7f0800fc (app launch screen)
        val res0fc = verifyTable.getResource(0x7f0800fc)
        println("Found 0x7f0800fc: ${res0fc?.name}")
        assertNotNull("0x7f0800fc should exist after merge", res0fc)
    }
}
