package app.pwhs.universalantisplit.engine.hook.axml

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

class ZInput(inputStream: InputStream) {
    private val dis: DataInputStream = DataInputStream(inputStream)
    private var size: Int = 0

    val offset: Int
        get() = size

    fun close() {
        dis.close()
    }

    fun readByte(): Byte {
        size++
        return dis.readByte()
    }

    fun readShort(): Short {
        size += 2
        val b1 = dis.read()
        val b2 = dis.read()
        return ((b2 shl 8) or (b1 and 0xFF)).toShort()
    }

    fun readInt(): Int {
        size += 4
        val b1 = dis.read()
        val b2 = dis.read()
        val b3 = dis.read()
        val b4 = dis.read()
        return (b4 shl 24) or ((b3 and 0xFF) shl 16) or ((b2 and 0xFF) shl 8) or (b1 and 0xFF)
    }

    fun readIntArray(length: Int): IntArray {
        val array = IntArray(length)
        for (i in 0 until length) {
            array[i] = readInt()
        }
        return array
    }

    fun readFully(b: ByteArray) {
        dis.readFully(b)
        size += b.size
    }

    fun skipInt() {
        readInt()
    }

    fun skipCheckChunkTypeInt(expected: Int, alternate: Int) {
        val type = readInt()
        if (type != expected && type != alternate) {
            throw IOException(
                String.format("Invalid chunk type: expected 0x%08x or 0x%08x, got 0x%08x", expected, alternate, type)
            )
        }
    }
}
