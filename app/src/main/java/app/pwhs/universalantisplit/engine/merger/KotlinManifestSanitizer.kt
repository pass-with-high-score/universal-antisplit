package app.pwhs.universalantisplit.engine.merger

import java.nio.charset.StandardCharsets

/**
 * Fallback Kotlin AXML Sanitizer that neutralizes split-related attributes
 * with byte-for-byte identical-length ASCII and UTF-16LE replacements.
 */
object KotlinManifestSanitizer {

    private val REPLACEMENTS = listOf(
        "com.android.vending.splits.required".toByteArray(StandardCharsets.US_ASCII) to "com.android.vending.splits.merged__".toByteArray(StandardCharsets.US_ASCII),
        "com.android.vending.splits".toByteArray(StandardCharsets.US_ASCII) to "com.android.vending.merged".toByteArray(StandardCharsets.US_ASCII),
        "requiredSplitTypes".toByteArray(StandardCharsets.US_ASCII) to "__req_SplitTypes__".toByteArray(StandardCharsets.US_ASCII),
        "extractNativeLibs".toByteArray(StandardCharsets.US_ASCII) to "__ext_NativeLibs_".toByteArray(StandardCharsets.US_ASCII),
        "configForSplit".toByteArray(StandardCharsets.US_ASCII) to "__cfgForSplit_".toByteArray(StandardCharsets.US_ASCII),
        "isFeatureSplit".toByteArray(StandardCharsets.US_ASCII) to "__isFeatSplit_".toByteArray(StandardCharsets.US_ASCII),
        "isolatedSplits".toByteArray(StandardCharsets.US_ASCII) to "__isolated_spt".toByteArray(StandardCharsets.US_ASCII),
        "splitTypes".toByteArray(StandardCharsets.US_ASCII) to "__sptTypes".toByteArray(StandardCharsets.US_ASCII),
        "split".toByteArray(StandardCharsets.US_ASCII) to "__spt".toByteArray(StandardCharsets.US_ASCII),
    )

    fun sanitize(axmlData: ByteArray): ByteArray {
        if (axmlData.size < 8) return axmlData
        val buffer = axmlData.copyOf()

        for ((target, replacement) in REPLACEMENTS) {
            replaceAll(buffer, target, replacement)
            val utf16Target = toUtf16Le(target)
            val utf16Replacement = toUtf16Le(replacement)
            replaceAll(buffer, utf16Target, utf16Replacement)
        }
        return buffer
    }

    private fun replaceAll(buffer: ByteArray, target: ByteArray, replacement: ByteArray) {
        if (target.isEmpty() || buffer.size < target.len()) return
        var i = 0
        while (i + target.size <= buffer.size) {
            var match = true
            for (j in target.indices) {
                if (buffer[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                System.arraycopy(replacement, 0, buffer, i, replacement.size)
                i += target.size
            } else {
                i++
            }
        }
    }

    private fun ByteArray.len(): Int = this.size

    private fun toUtf16Le(bytes: ByteArray): ByteArray {
        val out = ByteArray(bytes.size * 2)
        for (i in bytes.indices) {
            out[i * 2] = bytes[i]
            out[i * 2 + 1] = 0x00
        }
        return out
    }
}
