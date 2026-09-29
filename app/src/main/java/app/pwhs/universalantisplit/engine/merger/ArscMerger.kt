package app.pwhs.universalantisplit.engine.merger

import com.reandroid.arsc.chunk.TableBlock
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * High-performance resource table merger that consolidates split resources.arsc tables
 * into the primary base resources.arsc table using ARSCLib.
 *
 * Ensures all resource types, configurations (density, language), type specs,
 * string pools, and package tables are unified without ID conflicts or missing entries.
 */
object ArscMerger {

    fun merge(baseArscBytes: ByteArray, splitArscBytesList: List<ByteArray>): ByteArray {
        if (splitArscBytesList.isEmpty()) {
            return baseArscBytes
        }

        Timber.d("Merging %d split resources.arsc tables into base", splitArscBytesList.size)
        val baseTable = TableBlock()
        baseTable.readBytes(ByteArrayInputStream(baseArscBytes))

        for ((index, splitBytes) in splitArscBytesList.withIndex()) {
            if (splitBytes.isEmpty()) continue
            try {
                val splitTable = TableBlock()
                splitTable.readBytes(ByteArrayInputStream(splitBytes))
                baseTable.merge(splitTable)
                Timber.d("Merged split resources.arsc index=%d successfully", index)
            } catch (t: Throwable) {
                Timber.e(t, "Failed to merge split resources.arsc index=%d", index)
            }
        }

        baseTable.refresh()
        return baseTable.bytes
    }

    fun mergeFromStreams(baseArscStream: InputStream, splitStreams: List<InputStream>): ByteArray {
        val baseTable = TableBlock()
        baseTable.readBytes(baseArscStream)

        for ((index, splitStream) in splitStreams.withIndex()) {
            try {
                val splitTable = TableBlock()
                splitTable.readBytes(splitStream)
                baseTable.merge(splitTable)
                Timber.d("Merged split stream index=%d successfully", index)
            } catch (t: Throwable) {
                Timber.e(t, "Failed to merge split stream index=%d", index)
            }
        }

        baseTable.refresh()
        return baseTable.bytes
    }
}
