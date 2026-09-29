package app.pwhs.universalantisplit.engine.hook

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestHookPatcherTest {

    @Test
    fun testPatchSampleManifest() {
        val sampleFile = File("/Users/macmini0051/.gemini/antigravity-ide/brain/1b03bc45-7097-41bd-a36b-03c949c611af/scratch/sample_manifest.axml")
        if (!sampleFile.exists()) {
            return
        }

        val originalBytes = sampleFile.readBytes()
        val result = ManifestHookPatcher.patch(originalBytes)

        assertNotNull(result.patchedManifestBytes)
        assertTrue(result.patchedManifestBytes.isNotEmpty())
        assertNotNull(result.packageName)
        println("Package Name: ${result.packageName}")
        println("Original Application: ${result.originalApplicationClass}")
        println("Patched AXML size: ${result.patchedManifestBytes.size} bytes (original: ${originalBytes.size} bytes)")
    }
}
