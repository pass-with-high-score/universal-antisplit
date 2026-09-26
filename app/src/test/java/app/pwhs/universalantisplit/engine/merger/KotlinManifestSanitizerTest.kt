package app.pwhs.universalantisplit.engine.merger

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class KotlinManifestSanitizerTest {

    @Test
    fun testAsciiSanitization() {
        val input = "dummy_split_attribute_test".toByteArray(StandardCharsets.US_ASCII)
        val sanitized = KotlinManifestSanitizer.sanitize(input)
        val expected = "dummy___spt_attribute_test".toByteArray(StandardCharsets.US_ASCII)
        assertArrayEquals(expected, sanitized)
    }

    @Test
    fun testUtf16LeSanitization() {
        val originalStr = "<manifest split=\"config.arm64\">"
        val originalBytes = originalStr.toByteArray(StandardCharsets.UTF_16LE)
        val sanitized = KotlinManifestSanitizer.sanitize(originalBytes)

        val expectedStr = "<manifest __spt=\"config.arm64\">"
        val expectedBytes = expectedStr.toByteArray(StandardCharsets.UTF_16LE)
        assertArrayEquals(expectedBytes, sanitized)
    }

    @Test
    fun testPreservesLength() {
        val originalStr = "com.android.vending.splits and isFeatureSplit and isolatedSplits"
        val originalBytes = originalStr.toByteArray(StandardCharsets.US_ASCII)
        val sanitized = KotlinManifestSanitizer.sanitize(originalBytes)

        assertEquals(originalBytes.size, sanitized.size)
        val resultStr = String(sanitized, StandardCharsets.US_ASCII)
        assertEquals("com.android.vending.merged and __isFeatSplit_ and __isolated_spt", resultStr)
    }
}
