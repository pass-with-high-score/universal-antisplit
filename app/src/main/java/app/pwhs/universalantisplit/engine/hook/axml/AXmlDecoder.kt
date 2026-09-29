package app.pwhs.universalantisplit.engine.hook.axml

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class AXmlDecoder private constructor(private val input: ZInput) {
    lateinit var stringDecoder: StringDecoder
        private set
    var data: ByteArray = ByteArray(0)

    private fun readStrings() {
        val type = input.readInt()
        if (type != AXML_CHUNK_TYPE) {
            throw IOException(String.format("Invalid chunk type: expected=0x%08x, got=0x%08x", AXML_CHUNK_TYPE, type))
        }
        input.readInt() // Chunk size
        stringDecoder = StringDecoder.read(input)

        val byteOut = ByteArrayOutputStream()
        val buf = ByteArray(2048)
        var num: Int
        val dis = java.io.DataInputStream(java.io.ByteArrayInputStream(ByteArray(0))) // unused directly
        while (true) {
            val count = runCatching {
                val b = ByteArray(2048)
                val read = (input as Any) // read directly from input
                // Since ZInput wraps DataInputStream, we read remaining bytes
            }
            break
        }
    }

    companion object {
        const val AXML_CHUNK_TYPE = 0x00080003

        fun decode(stream: InputStream): AXmlDecoder {
            val zIn = ZInput(stream)
            val axml = AXmlDecoder(zIn)
            val type = zIn.readInt()
            if (type != AXML_CHUNK_TYPE) {
                throw IOException(String.format("Invalid chunk type: expected 0x%08x, got 0x%08x", AXML_CHUNK_TYPE, type))
            }
            zIn.readInt() // root chunk size
            axml.stringDecoder = StringDecoder.read(zIn)

            val byteOut = ByteArrayOutputStream()
            val buf = ByteArray(2048)
            var count: Int
            while (true) {
                val b = runCatching { zIn.readByte() }.getOrNull() ?: break
                byteOut.write(b.toInt())
            }
            axml.data = byteOut.toByteArray()
            zIn.close()
            return axml
        }
    }

    fun write(list: List<String>, out: OutputStream) {
        val zOut = ZOutput(out)
        val baos = ByteArrayOutputStream()
        val buf = ZOutput(baos)
        val array = list.toTypedArray()
        stringDecoder.write(array, buf)
        buf.writeFully(data)

        zOut.writeInt(AXML_CHUNK_TYPE)
        zOut.writeInt(baos.size() + 8)
        zOut.writeFully(baos.toByteArray())
        buf.close()
    }
}
