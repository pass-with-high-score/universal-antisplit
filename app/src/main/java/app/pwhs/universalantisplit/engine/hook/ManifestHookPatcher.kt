package app.pwhs.universalantisplit.engine.hook

import app.pwhs.universalantisplit.engine.hook.axml.AXmlDecoder
import app.pwhs.universalantisplit.engine.hook.axml.AXmlSimpleParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

data class ManifestPatchResult(
    val patchedManifestBytes: ByteArray,
    val packageName: String?,
    val originalApplicationClass: String?,
)

object ManifestHookPatcher {
    const val HOOK_APPLICATION_CLASS = "app.pwhs.universalantisplit.hook.PmsHookApplication"
    private const val ATTR_NAME_RESOURCE_ID = 0x01010003 // android.R.attr.name
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun patch(rawManifestBytes: ByteArray): ManifestPatchResult {
        val axml = AXmlDecoder.decode(ByteArrayInputStream(rawManifestBytes))
        val parser = AXmlSimpleParser(axml.data, axml.stringDecoder)

        var packageName: String? = null
        var customApplication = false
        var originalApplicationClass: String? = null
        var success = false

        var event = parser.next()
        while (event != AXmlSimpleParser.END_DOCUMENT) {
            if (event == AXmlSimpleParser.START_TAG) {
                val tag = parser.currentTagName
                if (tag == "manifest") {
                    for (i in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(i) == "package") {
                            packageName = parser.getAttributeValue(i)
                        }
                    }
                } else if (tag == "application") {
                    val hookStringIndex = axml.stringDecoder.getSize()
                    val size = parser.attributeCount

                    for (i in 0 until size) {
                        if (parser.getAttributeNameResource(i) == ATTR_NAME_RESOURCE_ID) {
                            customApplication = true
                            originalApplicationClass = parser.getAttributeValue(i)

                            val data = axml.data
                            var off = parser.currentAttributeStart + 20 * i
                            off += 8 // rawValue offset
                            writeInt(data, off, hookStringIndex)
                            off += 8 // data offset
                            writeInt(data, off, hookStringIndex)
                            break
                        }
                    }

                    if (!customApplication) {
                        val off = parser.currentAttributeStart
                        val data = axml.data
                        val newData = ByteArray(data.size + 20)
                        System.arraycopy(data, 0, newData, 0, off)
                        System.arraycopy(data, off, newData, off + 20, data.size - off)

                        // Update chunkSize (offset - 32)
                        val chunkSize = readInt(newData, off - 32)
                        writeInt(newData, off - 32, chunkSize + 20)

                        // Update attributeCount (offset - 8)
                        writeInt(newData, off - 8, size + 1)

                        val idIndex = parser.findResourceID(ATTR_NAME_RESOURCE_ID)
                        val nsIndex = axml.stringDecoder.find(ANDROID_NS)

                        // Fill the 20-byte attribute struct
                        writeInt(newData, off, if (nsIndex != -1) nsIndex else 0)
                        writeInt(newData, off + 4, if (idIndex != -1) idIndex else 0)
                        writeInt(newData, off + 8, hookStringIndex) // rawValue
                        writeInt(newData, off + 12, 0x03000008)     // TYPE_STRING
                        writeInt(newData, off + 16, hookStringIndex) // data

                        axml.data = newData
                    }

                    success = true
                    break
                }
            }
            event = parser.next()
        }

        if (!success) {
            throw IOException("Failed to locate <application> tag in AndroidManifest.xml")
        }

        val stringList = mutableListOf<String>()
        axml.stringDecoder.getStrings(stringList)
        stringList.add(HOOK_APPLICATION_CLASS)

        val baos = ByteArrayOutputStream()
        axml.write(stringList, baos)

        return ManifestPatchResult(
            patchedManifestBytes = baos.toByteArray(),
            packageName = packageName,
            originalApplicationClass = originalApplicationClass,
        )
    }

    private fun readInt(data: ByteArray, offset: Int): Int {
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        val b2 = data[offset + 2].toInt() and 0xFF
        val b3 = data[offset + 3].toInt() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        data[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
