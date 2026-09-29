package app.pwhs.universalantisplit.engine.hook.axml

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class StringDecoder {
    private var strings: Array<String> = emptyArray()
    private var styleOffsets: IntArray? = null
    private var styles: IntArray? = null
    var isUtf8: Boolean = false
        private set
    private var styleOffsetCount: Int = 0
    private var stylesOffset: Int = 0
    private var flags: Int = 0
    var chunkSize: Int = 0
        private set
    private var rawStringsSize: Int = 0

    fun getSize(): Int = strings.size

    fun getString(index: Int): String? {
        return if (index in strings.indices) strings[index] else null
    }

    fun setString(index: Int, s: String) {
        if (index in strings.indices) {
            strings[index] = s
        }
    }

    fun find(target: String?): Int {
        if (target == null) return -1
        for (i in strings.indices) {
            if (strings[i] == target) return i
        }
        return -1
    }

    fun getStrings(list: MutableList<String>) {
        list.addAll(strings)
    }

    fun write(s: Array<String>, out: ZOutput) {
        val outBuf = ByteArrayOutputStream()
        val led = ZOutput(outBuf)
        val size = s.size
        val offsets = IntArray(size)
        var len = 0

        val bOut = ByteArrayOutputStream()
        val mStrings = ZOutput(bOut)

        if (this.isUtf8) {
            for (i in 0 until size) {
                offsets[i] = len
                val varStr = s[i]
                val charBuf = varStr.toCharArray()
                val bLen = getVarBytes(charBuf.size)
                mStrings.writeFully(bLen)
                len += bLen.size

                val buf = varStr.toByteArray(StandardCharsets.UTF_8)
                val bByteLen = getVarBytes(buf.size)
                mStrings.writeFully(bByteLen)
                len += bByteLen.size
                mStrings.writeFully(buf)
                len += buf.size
                mStrings.writeByte(0)
                len += 1
            }
        } else {
            for (i in 0 until size) {
                offsets[i] = len
                val varStr = s[i]
                val charBuf = varStr.toCharArray()
                mStrings.writeShort(charBuf.size.toShort())
                for (c in charBuf) {
                    mStrings.writeChar(c)
                }
                mStrings.writeShort(0.toShort())
                len += charBuf.size * 2 + 4
            }
        }

        var newStringsSize = bOut.size()
        val sizeMod = newStringsSize % 4
        if (sizeMod != 0) {
            for (i in 0 until 4 - sizeMod) {
                bOut.write(0)
            }
            newStringsSize += 4 - sizeMod
        }
        val encodedStrings = bOut.toByteArray()

        led.writeInt(size)
        led.writeInt(styleOffsetCount)
        led.writeInt(flags)

        val stringsOffset = 28 + (size + styleOffsetCount) * 4
        led.writeInt(stringsOffset)
        val diff = newStringsSize - this.rawStringsSize
        led.writeInt(if (stylesOffset == 0) 0 else stylesOffset + diff * 4)

        led.writeIntArray(offsets)
        styleOffsets?.let { led.writeIntArray(it) }

        led.writeFully(encodedStrings)
        styles?.let { led.writeIntArray(it) }

        out.writeInt(CHUNK_STRINGPOOL_TYPE)
        val b = outBuf.toByteArray()
        outBuf.close()
        led.close()
        out.writeInt(b.size + 8)
        out.writeFully(b)
    }

    companion object {
        const val IS_UTF8 = 0x100
        private const val CHUNK_STRINGPOOL_TYPE = 0x001C0001
        private const val CHUNK_NULL_TYPE = 0x00000000

        fun read(input: ZInput): StringDecoder {
            input.skipCheckChunkTypeInt(CHUNK_STRINGPOOL_TYPE, CHUNK_NULL_TYPE)
            val block = StringDecoder()
            block.chunkSize = input.readInt()

            val stringCount = input.readInt()
            block.styleOffsetCount = input.readInt()
            block.flags = input.readInt()
            val stringsOffset = input.readInt()
            block.stylesOffset = input.readInt()

            block.isUtf8 = (block.flags and IS_UTF8) != 0
            val stringOffsets = input.readIntArray(stringCount)

            if (block.styleOffsetCount != 0) {
                block.styleOffsets = input.readIntArray(block.styleOffsetCount)
            }

            var size = (if (block.stylesOffset == 0) block.chunkSize else block.stylesOffset) - stringsOffset
            val data = ByteArray(size)
            input.readFully(data)
            block.rawStringsSize = size

            if (block.stylesOffset != 0) {
                size = block.chunkSize - block.stylesOffset
                block.styles = input.readIntArray(size / 4)
                var remaining = size % 4
                while (remaining-- > 0) {
                    input.readByte()
                }
            }

            block.strings = Array(stringOffsets.size) { "" }
            for (i in stringOffsets.indices) {
                var offset = stringOffsets[i]
                val length: Int
                if (!block.isUtf8) {
                    length = getShort(data, offset) * 2
                    offset += 2
                    block.strings[i] = decodeString(offset, length, false, data)
                } else {
                    offset += getVarint(data, offset)[1]
                    val varint = getVarint(data, offset)
                    offset += varint[1]
                    length = varint[0]
                    block.strings[i] = decodeString(offset, length, true, data)
                }
            }
            return block
        }

        private fun getVarint(array: ByteArray, offset: Int): IntArray {
            return if ((array[offset].toInt() and 0x80) == 0) {
                intArrayOf(array[offset].toInt() and 0x7f, 1)
            } else {
                intArrayOf(((array[offset].toInt() and 0x7f) shl 8) or (array[offset + 1].toInt() and 0xFF), 2)
            }
        }

        private fun getVarBytes(value: Int): ByteArray {
            return if ((value and 0x7f) == value) {
                byteArrayOf(value.toByte())
            } else {
                byteArrayOf(((value ushr 8) or 0x80).toByte(), (value and 0xFF).toByte())
            }
        }

        private fun decodeString(offset: Int, length: Int, utf8: Boolean, data: ByteArray): String {
            return runCatching {
                val decoder = if (utf8) StandardCharsets.UTF_8.newDecoder() else StandardCharsets.UTF_16LE.newDecoder()
                decoder.decode(ByteBuffer.wrap(data, offset, length)).toString()
            }.getOrDefault("")
        }

        private fun getShort(array: ByteArray, offset: Int): Int {
            return ((array[offset + 1].toInt() and 0xFF) shl 8) or (array[offset].toInt() and 0xFF)
        }
    }
}
