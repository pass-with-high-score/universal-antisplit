package app.pwhs.universalantisplit.engine.tracker

import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * Strips tracker components from a merged APK by:
 * 1. Scanning DEX class lists for known tracker signatures
 * 2. Neutralizing tracker-related manifest entries (services, receivers, providers)
 * 3. Removing tracker native libraries (.so files)
 * 4. Removing tracker-related permissions
 */
class TrackerStripper(private val trackerDatabase: TrackerDatabase) {

    /** Delegate to TrackerDatabase for getting package prefixes. */
    fun getTrackerPackagePrefixes(detected: List<TrackerEntry>): Set<String> {
        return trackerDatabase.getTrackerPackagePrefixes(detected)
    }

    data class StripResult(
        val detectedTrackers: List<TrackerEntry>,
        val strippedManifestEntries: Int,
        val strippedNativeLibs: List<String>,
        val strippedPermissions: List<String>,
    )

    /** Permissions commonly used exclusively by trackers / ads. */
    private val trackerPermissions = setOf(
        "com.google.android.gms.permission.AD_ID",
        "com.google.android.c2dm.permission.RECEIVE",
        "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
    )

    /** Known tracker native library name patterns. */
    private val trackerNativeLibPatterns = listOf(
        "libcrashlytics",
        "libcrashlytics-common",
        "libcrashlytics-handler",
        "libfacebook",
        "libfbjni",
        "libfb_abi",
        "libappsflyer",
        "libadjust",
        "libbranch",
        "libsentry",
        "libsentry-android",
        "libonesignal",
        "libamplitude",
        "libmixpanel",
        "libsegment",
    )

    /** Scan DEX class list from a ZIP (APK) and detect trackers. */
    fun detectTrackersInApk(apkFile: java.io.File): List<TrackerEntry> {
        trackerDatabase.load()
        val classNames = mutableSetOf<String>()

        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (name.matches(Regex("^classes\\d*\\.dex$"))) {
                    classNames.addAll(extractClassNamesFromDex(zip, entry))
                }
            }
        }

        Timber.d("Scanned ${classNames.size} classes from DEX files")
        return trackerDatabase.detectTrackers(classNames)
    }

    /** Determine if a ZIP entry name is a tracker native lib. */
    fun isTrackerNativeLib(entryName: String): Boolean {
        if (!entryName.startsWith("lib/") || !entryName.endsWith(".so")) return false
        val libName = entryName.substringAfterLast("/").removeSuffix(".so")
        return trackerNativeLibPatterns.any { libName.startsWith(it) }
    }

    /**
     * Neutralize tracker manifest entries by replacing package names in the binary AXML.
     * Uses the same byte-level replacement technique as KotlinManifestSanitizer.
     */
    fun neutralizeTrackerManifest(
        axmlData: ByteArray,
        detectedTrackers: List<TrackerEntry>,
    ): Pair<ByteArray, Int> {
        if (detectedTrackers.isEmpty()) return axmlData to 0

        val prefixes = trackerDatabase.getTrackerPackagePrefixes(detectedTrackers)
        val buffer = axmlData.copyOf()
        var neutralizedCount = 0

        for (prefix in prefixes) {
            val dotPrefix = prefix.replace('/', '.')
            if (dotPrefix.length < 5) continue // Skip too-short prefixes

            val target = dotPrefix.toByteArray(StandardCharsets.US_ASCII)
            val replacement = buildNeutralizedBytes(target)
            val count = replaceAllOccurrences(buffer, target, replacement)

            if (count > 0) {
                val utf16Target = toUtf16Le(target)
                val utf16Replacement = toUtf16Le(replacement)
                replaceAllOccurrences(buffer, utf16Target, utf16Replacement)
                neutralizedCount += count
            }
        }

        // Also neutralize tracker permissions
        for (perm in trackerPermissions) {
            val target = perm.toByteArray(StandardCharsets.US_ASCII)
            val replacement = buildNeutralizedBytes(target)
            val count = replaceAllOccurrences(buffer, target, replacement)
            if (count > 0) {
                val utf16Target = toUtf16Le(target)
                val utf16Replacement = toUtf16Le(replacement)
                replaceAllOccurrences(buffer, utf16Target, utf16Replacement)
                neutralizedCount += count
            }
        }

        Timber.i("Neutralized $neutralizedCount tracker manifest entries")
        return buffer to neutralizedCount
    }

    /** Build a neutralized byte array of the same length. Prefix with "__stripped_". */
    private fun buildNeutralizedBytes(original: ByteArray): ByteArray {
        val prefix = "__stripped_".toByteArray(StandardCharsets.US_ASCII)
        val result = ByteArray(original.size)
        val prefixLen = prefix.size.coerceAtMost(original.size)
        System.arraycopy(prefix, 0, result, 0, prefixLen)
        // Fill remaining with underscores
        for (i in prefixLen until original.size) {
            result[i] = '_'.code.toByte()
        }
        return result
    }

    private fun replaceAllOccurrences(
        buffer: ByteArray,
        target: ByteArray,
        replacement: ByteArray,
    ): Int {
        if (target.isEmpty() || buffer.size < target.size) return 0
        var count = 0
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
                count++
                i += target.size
            } else {
                i++
            }
        }
        return count
    }

    private fun toUtf16Le(bytes: ByteArray): ByteArray {
        val out = ByteArray(bytes.size * 2)
        for (i in bytes.indices) {
            out[i * 2] = bytes[i]
            out[i * 2 + 1] = 0x00
        }
        return out
    }

    /**
     * Extract class names from a DEX file entry (reads DEX string table).
     * Only extracts strings that look like class descriptors (L...;).
     */
    private fun extractClassNamesFromDex(
        zip: ZipFile,
        entry: java.util.zip.ZipEntry,
    ): Set<String> {
        val classes = mutableSetOf<String>()
        try {
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            // DEX magic: "dex\n035\0" or similar
            if (bytes.size < 112) return classes

            val stringIdsOff = readInt(bytes, 60)
            val stringIdsSize = readInt(bytes, 56)

            for (i in 0 until stringIdsSize.coerceAtMost(50000)) {
                val off = readInt(bytes, stringIdsOff + i * 4)
                if (off < 0 || off >= bytes.size) continue
                val str = readMutf8String(bytes, off) ?: continue
                // Class descriptors: Lcom/facebook/ads/Ad;
                if (str.startsWith("L") && str.endsWith(";") && str.contains("/")) {
                    // Convert: Lcom/facebook/ads/Ad; -> com/facebook/ads/Ad.class
                    val className = str.substring(1, str.length - 1) + ".class"
                    classes.add(className)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse DEX class names from ${entry.name}")
        }
        return classes
    }

    private fun readInt(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return 0
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    /** Read a MUTF-8 string from DEX string data (LEB128 length prefix). */
    private fun readMutf8String(data: ByteArray, offset: Int): String? {
        var pos = offset
        // Skip ULEB128 length
        while (pos < data.size && (data[pos].toInt() and 0x80) != 0) pos++
        if (pos >= data.size) return null
        pos++ // skip final byte of ULEB128

        val sb = StringBuilder()
        var count = 0
        while (pos < data.size && count < 512) {
            val b = data[pos].toInt() and 0xFF
            if (b == 0) break
            if (b < 0x80) {
                sb.append(b.toChar())
                pos++
            } else if (b and 0xE0 == 0xC0) {
                if (pos + 1 >= data.size) break
                val b2 = data[pos + 1].toInt() and 0xFF
                sb.append(((b and 0x1F shl 6) or (b2 and 0x3F)).toChar())
                pos += 2
            } else {
                if (pos + 2 >= data.size) break
                val b2 = data[pos + 1].toInt() and 0xFF
                val b3 = data[pos + 2].toInt() and 0xFF
                sb.append(((b and 0x0F shl 12) or (b2 and 0x3F shl 6) or (b3 and 0x3F)).toChar())
                pos += 3
            }
            count++
        }
        return if (sb.isEmpty()) null else sb.toString()
    }
}
