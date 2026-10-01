package app.pwhs.universalantisplit.engine

import app.pwhs.universalantisplit.engine.ApkFileInfoReader.Companion.abisFromEntryNames
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkFileInfoReaderAbiTest {

    @Test
    fun `distinct abi dirs are returned sorted`() {
        val entries = listOf(
            "lib/arm64-v8a/libfoo.so",
            "lib/armeabi-v7a/libfoo.so",
            "lib/arm64-v8a/libbar.so",
        )

        val abis = abisFromEntryNames(entries)

        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), abis)
    }

    @Test
    fun `non-native entries are ignored`() {
        val entries = listOf(
            "classes.dex",
            "res/layout/main.xml",
            "lib/x86/libnative.so",
            "AndroidManifest.xml",
        )

        val abis = abisFromEntryNames(entries)

        assertEquals(listOf("x86"), abis)
    }

    @Test
    fun `nested paths and non-so files under lib do not count as abi`() {
        val entries = listOf(
            "lib/arm64-v8a/sub/deep.so",
            "lib/arm64-v8a/notes.txt",
            "lib/x86_64/libok.so",
        )

        val abis = abisFromEntryNames(entries)

        assertEquals(listOf("x86_64"), abis)
    }

    @Test
    fun `apk with no native libs yields empty list`() {
        val entries = listOf("classes.dex", "resources.arsc")

        val abis = abisFromEntryNames(entries)

        assertEquals(emptyList<String>(), abis)
    }
}
