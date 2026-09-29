package app.pwhs.universalantisplit.engine.merger

import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Android APK-aware ZipWriter that enforces:
 * 1. resources.arsc stored uncompressed (STORED) and aligned on a 4-byte boundary (required by Android R+ / API 30+).
 * 2. Native libraries (.so files in lib directory) stored uncompressed (STORED) and aligned on a 16KB (or 4KB) boundary for Android 15+.
 * 3. Preserves uncompressed method for all other STORED entries with 4-byte alignment.
 */
class ApkZipWriter(
    outputStream: OutputStream,
    private val align16Kb: Boolean = true,
) : AutoCloseable {

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count: Long = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    private val countingStream = CountingOutputStream(outputStream)
    private val zos = ZipOutputStream(countingStream)

    fun writeEntry(
        name: String,
        data: ByteArray,
        originalMethod: Int = ZipEntry.DEFLATED,
    ) {
        val isResourcesArsc = name == "resources.arsc"
        val isNativeLib = name.startsWith("lib/") && name.endsWith(".so")
        val mustStore = isResourcesArsc || isNativeLib || originalMethod == ZipEntry.STORED

        val alignment = when {
            !mustStore -> 0
            isNativeLib -> if (align16Kb) 16384 else 4096
            else -> 4 // resources.arsc and other uncompressed assets must be 4-byte aligned
        }

        val entry = ZipEntry(name)
        if (mustStore) {
            entry.method = ZipEntry.STORED
            entry.size = data.size.toLong()
            entry.compressedSize = data.size.toLong()
            val crc = CRC32()
            crc.update(data)
            entry.crc = crc.value

            if (alignment > 1) {
                val currentOffset = countingStream.count
                val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
                val baseDataOffset = currentOffset + 30 + nameBytes.size
                val rem = (baseDataOffset % alignment).toInt()
                val padding = if (rem != 0) alignment - rem else 0
                if (padding > 0) {
                    entry.extra = ByteArray(padding)
                }
            }
        } else {
            entry.method = ZipEntry.DEFLATED
        }

        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    fun copyEntry(sourceZip: ZipFile, entry: ZipEntry, outputName: String = entry.name) {
        val bytes = sourceZip.getInputStream(entry).use { it.readBytes() }
        writeEntry(
            name = outputName,
            data = bytes,
            originalMethod = entry.method,
        )
    }

    override fun close() {
        zos.close()
        countingStream.close()
    }
}
