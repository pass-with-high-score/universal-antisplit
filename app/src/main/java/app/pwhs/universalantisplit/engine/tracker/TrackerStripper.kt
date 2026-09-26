package app.pwhs.universalantisplit.engine.tracker

import timber.log.Timber
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipFile

/**
 * Strips tracker components from a merged APK by:
 * 1. Scanning DEX class lists for known tracker signatures (Exodus Privacy)
 * 2. Neutralizing tracker-related manifest entries and permissions in-place
 * 3. Removing tracker native libraries (.so files)
 * 4. Neutralizing DEX class descriptors and recalculating DEX checksums
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

    /** Scan DEX class list across multiple APK files and detect trackers. */
    fun detectTrackersInApks(apkFiles: List<File>): List<TrackerEntry> {
        trackerDatabase.load()
        val classNames = mutableSetOf<String>()

        for (file in apkFiles) {
            if (!file.exists()) continue
            try {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name.matches(Regex("^classes\\d*\\.dex$"))) {
                            classNames.addAll(extractClassNamesFromDex(zip, entry))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to extract classes from ${file.name}")
            }
        }

        Timber.d("Scanned ${classNames.size} classes from DEX files across ${apkFiles.size} APKs")
        return trackerDatabase.detectTrackers(classNames)
    }

    /** Scan DEX class list from a single APK file and detect trackers. */
    fun detectTrackersInApk(apkFile: File): List<TrackerEntry> {
        return detectTrackersInApks(listOf(apkFile))
    }

    /** Determine if a ZIP entry name is a tracker native lib. */
    fun isTrackerNativeLib(entryName: String): Boolean {
        if (!entryName.startsWith("lib/") || !entryName.endsWith(".so")) return false
        val libName = entryName.substringAfterLast("/").removeSuffix(".so")
        return trackerNativeLibPatterns.any { libName.startsWith(it) }
    }

    /**
     * Mutates the last byte of an ASCII pattern by +/- 1 so the string length
     * remains identical without shifting bytecode/offsets or corrupting tables.
     */
    fun mutatePattern(pattern: ByteArray): ByteArray {
        val result = pattern.copyOf()
        if (result.isNotEmpty()) {
            val last = result[result.size - 1].toInt() and 0xFF
            val next = if (last >= 126) last - 1 else last + 1
            result[result.size - 1] = next.toByte()
        }
        return result
    }

    /**
     * Neutralize tracker manifest entries by mutating package names in the binary AXML.
     * Preserves exact length to avoid corrupting string offsets or pool lengths.
     */
    fun neutralizeTrackerManifest(
        axmlData: ByteArray,
        detectedTrackers: List<TrackerEntry>,
    ): Pair<ByteArray, Int> {
        if (detectedTrackers.isEmpty()) return axmlData to 0

        val manifestPatterns = detectedTrackers.flatMap { entry ->
            entry.code.split("|").map { it.trim().removeSuffix(".") }
        }.filter { it.length >= 5 }
            .distinct()
            .sortedByDescending { it.length }

        val buffer = axmlData.copyOf()
        var neutralizedCount = 0

        for (patternStr in manifestPatterns) {
            val target = patternStr.toByteArray(StandardCharsets.US_ASCII)
            val replacement = mutatePattern(target)
            val count8 = replaceAllOccurrences(buffer, target, replacement)

            val utf16Target = toUtf16Le(target)
            val utf16Replacement = toUtf16Le(replacement)
            val count16 = replaceAllOccurrences(buffer, utf16Target, utf16Replacement)

            neutralizedCount += (count8 + count16)
        }

        // Also neutralize tracker permissions
        for (perm in trackerPermissions) {
            val target = perm.toByteArray(StandardCharsets.US_ASCII)
            val replacement = mutatePattern(target)
            val count8 = replaceAllOccurrences(buffer, target, replacement)

            val utf16Target = toUtf16Le(target)
            val utf16Replacement = toUtf16Le(replacement)
            val count16 = replaceAllOccurrences(buffer, utf16Target, utf16Replacement)

            neutralizedCount += (count8 + count16)
        }

        Timber.i("Neutralized $neutralizedCount tracker manifest occurrences")
        return buffer to neutralizedCount
    }

    /**
     * Neutralizes class descriptors in DEX files (e.g. Lcom/facebook/ads/) in-place,
     * then recalculates SHA-1 and Adler32 checksums in the DEX header.
     */
    fun neutralizeDexTrackers(
        dexBytes: ByteArray,
        detectedTrackers: List<TrackerEntry>,
    ): ByteArray {
        if (detectedTrackers.isEmpty() || dexBytes.size < 112) return dexBytes
        if (dexBytes[0] != 'd'.code.toByte() || dexBytes[1] != 'e'.code.toByte() ||
            dexBytes[2] != 'x'.code.toByte() || dexBytes[3] != '\n'.code.toByte()
        ) {
            return dexBytes
        }

        val dexPatterns = detectedTrackers.flatMap { entry ->
            entry.code.split("|").map { sig ->
                "L" + sig.trim().removeSuffix(".").replace('.', '/')
            }
        }.filter { it.length >= 6 }
            .distinct()
            .sortedByDescending { it.length }

        val buffer = dexBytes.copyOf()
        var totalReplaced = 0

        for (patternStr in dexPatterns) {
            val target = patternStr.toByteArray(StandardCharsets.US_ASCII)
            val replacement = mutatePattern(target)
            val count = replaceAllOccurrences(buffer, target, replacement)
            totalReplaced += count
        }

        if (totalReplaced > 0) {
            updateDexChecksums(buffer)
            Timber.d("Neutralized $totalReplaced tracker descriptors in DEX file")
        }
        return buffer
    }

    /**
     * Updates SHA-1 and Adler32 checksums in the DEX header:
     * - SHA-1 (bytes 12..31) covers bytes 32..end
     * - Adler32 (bytes 8..11, little-endian) covers bytes 12..end
     */
    private fun updateDexChecksums(buffer: ByteArray) {
        if (buffer.size < 112) return
        try {
            val md = MessageDigest.getInstance("SHA-1")
            md.update(buffer, 32, buffer.size - 32)
            val sha1 = md.digest()
            System.arraycopy(sha1, 0, buffer, 12, 20)

            val adler = Adler32()
            adler.update(buffer, 12, buffer.size - 12)
            val checksum = adler.value
            buffer[8] = (checksum and 0xFF).toByte()
            buffer[9] = ((checksum shr 8) and 0xFF).toByte()
            buffer[10] = ((checksum shr 16) and 0xFF).toByte()
            buffer[11] = ((checksum shr 24) and 0xFF).toByte()
        } catch (e: Exception) {
            Timber.e(e, "Failed to update DEX checksums")
        }
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
            if (bytes.size < 112) return classes

            val stringIdsOff = readInt(bytes, 60)
            val stringIdsSize = readInt(bytes, 56)

            for (i in 0 until stringIdsSize.coerceAtMost(50000)) {
                val off = readInt(bytes, stringIdsOff + i * 4)
                if (off < 0 || off >= bytes.size) continue
                val str = readMutf8String(bytes, off) ?: continue
                if (str.startsWith("L") && str.endsWith(";") && str.contains("/")) {
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
        while (pos < data.size && (data[pos].toInt() and 0x80) != 0) pos++
        if (pos >= data.size) return null
        pos++

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
