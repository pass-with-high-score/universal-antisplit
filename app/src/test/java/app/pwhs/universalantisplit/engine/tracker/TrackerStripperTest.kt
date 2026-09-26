package app.pwhs.universalantisplit.engine.tracker

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.Adler32

class TrackerStripperTest {

    private val mockDb = mockk<TrackerDatabase>(relaxed = true)
    private val stripper = TrackerStripper(mockDb)

    @Test
    fun testMutatePatternPreservesLength() {
        val original = "com.google.android.gms.ads".toByteArray(StandardCharsets.US_ASCII)
        val mutated = stripper.mutatePattern(original)

        assertEquals(original.size, mutated.size)
        assertFalse(original.contentEquals(mutated))
        // Verify prefix is preserved
        for (i in 0 until original.size - 1) {
            assertEquals(original[i], mutated[i])
        }
    }

    @Test
    fun testIsTrackerNativeLib() {
        assertTrue(stripper.isTrackerNativeLib("lib/arm64-v8a/libcrashlytics.so"))
        assertTrue(stripper.isTrackerNativeLib("lib/x86/libfacebook.so"))
        assertTrue(stripper.isTrackerNativeLib("lib/armeabi-v7a/libappsflyer.so"))
        assertFalse(stripper.isTrackerNativeLib("lib/arm64-v8a/libnative-lib.so"))
        assertFalse(stripper.isTrackerNativeLib("assets/libcrashlytics.so"))
    }

    @Test
    fun testNeutralizeDexTrackersRecalculatesChecksums() {
        // Construct a mock 120-byte DEX buffer
        val dexBuffer = ByteArray(128)
        // Magic: dex\n035\0
        val magic = "dex\n035\u0000".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(magic, 0, dexBuffer, 0, magic.size)

        // Put a tracker descriptor string at offset 70
        val descriptor = "Lcom/google/android/gms/ads/AdActivity;".toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(descriptor, 0, dexBuffer, 70, descriptor.size)

        val tracker = TrackerEntry(
            id = 1,
            name = "Google AdMob",
            code = "com.google.android.gms.ads"
        )

        val cleaned = stripper.neutralizeDexTrackers(dexBuffer, listOf(tracker))

        // Descriptor should no longer be present in cleaned
        val cleanedStr = String(cleaned, StandardCharsets.US_ASCII)
        assertFalse(cleanedStr.contains("Lcom/google/android/gms/ads/"))

        // Verify SHA-1
        val md = MessageDigest.getInstance("SHA-1")
        md.update(cleaned, 32, cleaned.size - 32)
        val expectedSha1 = md.digest()
        val actualSha1 = cleaned.copyOfRange(12, 32)
        assertTrue(expectedSha1.contentEquals(actualSha1))

        // Verify Adler32
        val adler = Adler32()
        adler.update(cleaned, 12, cleaned.size - 12)
        val expectedAdler = adler.value
        val actualAdler = (cleaned[8].toLong() and 0xFF) or
                ((cleaned[9].toLong() and 0xFF) shl 8) or
                ((cleaned[10].toLong() and 0xFF) shl 16) or
                ((cleaned[11].toLong() and 0xFF) shl 24)
        assertEquals(expectedAdler, actualAdler)
    }

    @Test
    fun testNeutralizeTrackerManifestInPlace() {
        val originalManifest = "prefix_com.google.android.gms.ads_suffix".toByteArray(StandardCharsets.US_ASCII)
        val tracker = TrackerEntry(
            id = 1,
            name = "Google AdMob",
            code = "com.google.android.gms.ads"
        )

        val (patched, count) = stripper.neutralizeTrackerManifest(originalManifest, listOf(tracker))

        assertEquals(originalManifest.size, patched.size)
        assertTrue(count > 0)
        val patchedStr = String(patched, StandardCharsets.US_ASCII)
        assertFalse(patchedStr.contains("com.google.android.gms.ads"))
    }
}
