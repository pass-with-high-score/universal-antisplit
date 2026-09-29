package app.pwhs.universalantisplit.engine.hook.axml

import java.io.IOException
import java.io.OutputStream

class ZOutput(private val dos: OutputStream) {
    private var written: Int = 0

    fun size(): Int = written

    fun close() {
        dos.close()
    }

    fun write(b: Int) {
        dos.write(b)
        written++
    }

    fun writeByte(b: Int) {
        dos.write(b)
        written++
    }

    fun writeShort(s: Short) {
        val v = s.toInt()
        dos.write(v and 0xFF)
        written++
        dos.write((v ushr 8) and 0xFF)
        written++
    }

    fun writeChar(c: Char) {
        val v = c.code
        dos.write(v and 0xFF)
        written++
        dos.write((v ushr 8) and 0xFF)
        written++
    }

    fun writeInt(i: Int) {
        dos.write(i and 0xFF)
        written++
        dos.write((i ushr 8) and 0xFF)
        written++
        dos.write((i ushr 16) and 0xFF)
        written++
        dos.write((i ushr 24) and 0xFF)
        written++
    }

    fun writeIntArray(array: IntArray) {
        for (i in array) {
            writeInt(i)
        }
    }

    fun writeFully(bytes: ByteArray) {
        dos.write(bytes)
        written += bytes.size
    }
}
