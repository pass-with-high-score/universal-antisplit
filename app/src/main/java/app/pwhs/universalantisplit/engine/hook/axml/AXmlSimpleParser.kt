package app.pwhs.universalantisplit.engine.hook.axml

import java.io.ByteArrayInputStream
import java.io.IOException

class AXmlSimpleParser(
    data: ByteArray,
    private val stringDecoder: StringDecoder,
) {
    private val input = ZInput(ByteArrayInputStream(data))
    private var resourceIDs: IntArray = IntArray(0)

    var currentTagName: String? = null
        private set
    var currentAttributeStart: Int = 0
        private set
    var attributeCount: Int = 0
        private set

    // Each attribute has 5 integers: [nsUri, name, rawValue, type, data]
    private var attributes: IntArray = IntArray(0)

    init {
        // Check if first chunk is CHUNK_RESOURCEIDS
        runCatching {
            val chunkType = input.readInt()
            if (chunkType == CHUNK_RESOURCEIDS) {
                val chunkSize = input.readInt()
                if (chunkSize >= 8 && chunkSize % 4 == 0) {
                    resourceIDs = input.readIntArray(chunkSize / 4 - 2)
                }
            } else {
                // If not resource IDs, it might already be an XML node
                // But normally in AXML, CHUNK_RESOURCEIDS comes immediately after StringPool
            }
        }
    }

    fun next(): Int {
        while (true) {
            val chunkType = runCatching { input.readInt() }.getOrNull() ?: return END_DOCUMENT

            if (chunkType == CHUNK_RESOURCEIDS) {
                val chunkSize = input.readInt()
                if (chunkSize >= 8 && chunkSize % 4 == 0) {
                    resourceIDs = input.readIntArray(chunkSize / 4 - 2)
                }
                continue
            }

            if (chunkType !in CHUNK_XML_FIRST..CHUNK_XML_LAST) {
                return END_DOCUMENT
            }

            // Header
            input.skipInt() // chunkSize
            input.skipInt() // lineNumber
            input.skipInt() // comment (0xFFFFFFFF)

            if (chunkType == CHUNK_XML_START_NAMESPACE || chunkType == CHUNK_XML_END_NAMESPACE) {
                input.skipInt() // prefix
                input.skipInt() // uri
                continue
            }

            if (chunkType == CHUNK_XML_START_TAG) {
                input.skipInt() // nsUri
                val nameIdx = input.readInt()
                currentTagName = stringDecoder.getString(nameIdx)
                input.skipInt() // flags

                val countRaw = input.readInt()
                attributeCount = countRaw and 0xFFFF
                input.skipInt() // class / style attribute

                currentAttributeStart = input.offset
                attributes = input.readIntArray(attributeCount * 5)
                return START_TAG
            }

            if (chunkType == CHUNK_XML_END_TAG) {
                input.skipInt() // nsUri
                input.skipInt() // name
                return END_TAG
            }

            if (chunkType == CHUNK_XML_TEXT) {
                input.skipInt() // name
                input.skipInt() // rawValue
                input.skipInt() // typedValue
                continue
            }
        }
    }

    fun getAttributeName(index: Int): String? {
        val offset = index * 5
        if (offset + 1 >= attributes.size) return null
        val nameIdx = attributes[offset + 1]
        return stringDecoder.getString(nameIdx)
    }

    fun getAttributeValue(index: Int): String? {
        val offset = index * 5
        if (offset + 4 >= attributes.size) return null
        val dataIdx = attributes[offset + 4]
        return stringDecoder.getString(dataIdx)
    }

    fun getAttributeNameResource(index: Int): Int {
        val offset = index * 5
        if (offset + 1 >= attributes.size) return 0
        val nameIdx = attributes[offset + 1]
        if (nameIdx in resourceIDs.indices) {
            return resourceIDs[nameIdx]
        }
        return 0
    }

    fun findResourceID(id: Int): Int {
        for (i in resourceIDs.indices) {
            if (resourceIDs[i] == id) return i
        }
        return -1
    }

    companion object {
        const val START_TAG = 2
        const val END_TAG = 3
        const val END_DOCUMENT = 1

        private const val CHUNK_RESOURCEIDS = 0x00080180
        private const val CHUNK_XML_FIRST = 0x00100100
        private const val CHUNK_XML_START_NAMESPACE = 0x00100100
        private const val CHUNK_XML_END_NAMESPACE = 0x00100101
        private const val CHUNK_XML_START_TAG = 0x00100102
        private const val CHUNK_XML_END_TAG = 0x00100103
        private const val CHUNK_XML_TEXT = 0x00100104
        private const val CHUNK_XML_LAST = 0x00100104
    }
}
