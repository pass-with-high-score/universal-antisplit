package app.pwhs.universalantisplit.engine.hook

import android.content.Context
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.engine.merger.ApkZipWriter
import timber.log.Timber
import java.io.InputStream
import java.nio.charset.StandardCharsets

class PmsHookInjector(private val context: Context) {

    /**
     * Injects the PMS Hook into the merged APK ZipWriter:
     * 1. Inserts antisplit_pms_hook.dex as classes{nextDexIndex}.dex
     * 2. Inserts assets/antisplit_signatures.bin
     * 3. Inserts assets/antisplit_real_app.txt (if original Application exists)
     *
     * Returns the new highest DEX index.
     */
    fun injectHook(
        writer: ApkZipWriter,
        nextDexIndex: Int,
        originalSignatures: List<ByteArray>,
        originalApplicationClass: String?,
        existingEntries: MutableSet<String>,
    ): Int {
        if (originalSignatures.isEmpty()) {
            Timber.w("No original signatures provided for PMS Hook injection")
            return nextDexIndex
        }

        // 1. Write Hook DEX
        val hookDexName = "classes$nextDexIndex.dex"
        if (hookDexName !in existingEntries) {
            val hookDexBytes = loadHookDexBytes()
            if (hookDexBytes != null && hookDexBytes.isNotEmpty()) {
                writer.writeEntry(hookDexName, hookDexBytes)
                existingEntries.add(hookDexName)
                Timber.i("Injected PMS Hook DEX as $hookDexName (${hookDexBytes.size} bytes)")
            } else {
                Timber.e("Failed to load PMS Hook DEX from resources")
            }
        }

        // 2. Write assets/antisplit_signatures.bin
        val sigEntryName = "assets/antisplit_signatures.bin"
        if (sigEntryName !in existingEntries) {
            val encodedSigs = SignatureExtractor.encodeCertificates(originalSignatures)
            writer.writeEntry(sigEntryName, encodedSigs)
            existingEntries.add(sigEntryName)
            Timber.i("Injected $sigEntryName (${encodedSigs.size} bytes)")
        }

        // 3. Write assets/antisplit_real_app.txt if custom application was defined
        if (!originalApplicationClass.isNullOrBlank() &&
            originalApplicationClass != ManifestHookPatcher.HOOK_APPLICATION_CLASS &&
            originalApplicationClass != "android.app.Application"
        ) {
            val realAppEntryName = "assets/antisplit_real_app.txt"
            if (realAppEntryName !in existingEntries) {
                val realAppBytes = originalApplicationClass.trim().toByteArray(StandardCharsets.UTF_8)
                writer.writeEntry(realAppEntryName, realAppBytes)
                existingEntries.add(realAppEntryName)
                Timber.i("Injected $realAppEntryName pointing to $originalApplicationClass")
            }
        }

        return nextDexIndex
    }

    private fun loadHookDexBytes(): ByteArray? {
        return runCatching {
            context.resources.openRawResource(R.raw.antisplit_pms_hook).use { input: InputStream ->
                input.readBytes()
            }
        }.onFailure {
            Timber.e(it, "Error opening raw resource antisplit_pms_hook")
        }.getOrNull()
    }
}
