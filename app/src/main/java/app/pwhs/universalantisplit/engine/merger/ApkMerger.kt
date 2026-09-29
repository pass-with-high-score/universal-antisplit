package app.pwhs.universalantisplit.engine.merger

import android.content.Context
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.engine.RustAntiSplitBridge
import app.pwhs.universalantisplit.engine.signer.ApkSignerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import app.pwhs.universalantisplit.engine.hook.ManifestHookPatcher
import app.pwhs.universalantisplit.engine.hook.PmsHookInjector
import app.pwhs.universalantisplit.engine.hook.SignatureExtractor

data class MergeOptions(
    val autoSign: Boolean = true,
    val bypassSignature: Boolean = false,
)

/**
 * High-performance APK Merger engine that unifies base APK and selected Split APKs
 * into a single standalone monolithic APK.
 */
class ApkMerger(
    private val context: Context,
    private val signerManager: ApkSignerManager = ApkSignerManager(context),
) {

    private val dexRegex = Regex("""^classes(\d*)\.dex$""")

    suspend fun merge(
        baseApkFile: File,
        splitFiles: List<File>,
        outputApkFile: File,
        options: MergeOptions = MergeOptions(),
        onProgress: (progress: Float, statusResId: Int, count: Int) -> Unit = { _, _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempUnsignedApk = File(context.cacheDir, "unsigned_merge_${System.currentTimeMillis()}.apk")
        var success = false

        try {
            onProgress(0.1f, R.string.status_analyzing_structure, splitFiles.size + 1)
            if (!baseApkFile.exists() || baseApkFile.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Base APK is invalid or missing"))
            }
            Timber.i("Beginning merge of base=${baseApkFile.name} with ${splitFiles.size} splits")

            val existingEntryNames = mutableSetOf<String>()
            val originalSignatures = if (options.bypassSignature) {
                SignatureExtractor.extractCertificates(baseApkFile)
            } else {
                emptyList()
            }

            var mergedViaRust = false
            if (RustAntiSplitBridge.isNativeEngineAvailable() && !options.bypassSignature) {
                onProgress(0.3f, R.string.status_merging_resources, 0)
                val splitPaths = splitFiles.filter { it.exists() && it.length() > 0 }
                    .map { it.absolutePath }
                    .toTypedArray()
                val rustOk = runCatching {
                    RustAntiSplitBridge.nativeMergeSplits(
                        baseApkFile.absolutePath,
                        splitPaths,
                        tempUnsignedApk.absolutePath,
                    )
                }.getOrDefault(false)

                if (rustOk && tempUnsignedApk.exists() && tempUnsignedApk.length() > 0) {
                    Timber.i("Splits merged via Rust Native Engine successfully: ${tempUnsignedApk.length()} bytes")
                    mergedViaRust = true
                } else {
                    Timber.w("Rust native merge failed or incomplete, falling back to Kotlin engine")
                }
            }

            if (!mergedViaRust) {
                ApkZipWriter(BufferedOutputStream(FileOutputStream(tempUnsignedApk)), align16Kb = true).use { writer ->
                    // Step 1: Process base.apk
                    onProgress(0.3f, R.string.status_cleaning_manifest, 0)
                    val baseResult = processBaseApk(
                        baseApkFile = baseApkFile,
                        writer = writer,
                        existingEntries = existingEntryNames,
                        bypassSignature = options.bypassSignature,
                    )
                    var currentDexIndex = baseResult.maxDex

                    // Step 2: Merge each split APK
                    val splitArscList = mutableListOf<ByteArray>()
                    onProgress(0.5f, R.string.status_merging_resources, 0)
                    for ((index, splitFile) in splitFiles.withIndex()) {
                        if (!splitFile.exists() || splitFile.length() == 0L) continue
                        val progressRatio = 0.5f + (0.3f * (index + 1) / splitFiles.size.coerceAtLeast(1))
                        onProgress(progressRatio, R.string.status_merging_resources, index + 1)

                        currentDexIndex = mergeSplitApk(
                            splitFile = splitFile,
                            writer = writer,
                            existingEntries = existingEntryNames,
                            currentDexIndex = currentDexIndex,
                            splitArscList = splitArscList,
                        )
                    }

                    // Step 2.1: Write merged resources.arsc table
                    if (baseResult.baseArscBytes != null) {
                        onProgress(0.81f, R.string.status_merging_resources, 0)
                        val finalArscBytes = if (splitArscList.isNotEmpty()) {
                            runCatching {
                                ArscMerger.merge(baseResult.baseArscBytes, splitArscList)
                            }.getOrElse { error ->
                                Timber.e(error, "Failed to merge resources.arsc, falling back to base")
                                baseResult.baseArscBytes
                            }
                        } else {
                            baseResult.baseArscBytes
                        }
                        writer.writeEntry("resources.arsc", finalArscBytes, ZipEntry.STORED)
                        existingEntryNames.add("resources.arsc")
                    }

                    // Step 2.5: Inject PMS Hook if enabled
                    if (options.bypassSignature && originalSignatures.isNotEmpty()) {
                        onProgress(0.82f, R.string.status_injecting_pms_hook, 0)
                        currentDexIndex = PmsHookInjector(context).injectHook(
                            writer = writer,
                            nextDexIndex = currentDexIndex + 1,
                            originalSignatures = originalSignatures,
                            originalApplicationClass = baseResult.originalApplicationClass,
                            existingEntries = existingEntryNames,
                        )
                    }
                }
            }

            // Step 3: Sign the APK or copy directly
            if (options.autoSign) {
                onProgress(0.85f, R.string.status_signing_apk, 0)
                val signResult = signerManager.signApk(
                    inputApk = tempUnsignedApk,
                    outputApk = outputApkFile
                )
                if (signResult.isFailure) {
                    throw signResult.exceptionOrNull() ?: IllegalStateException("Signing failed")
                }
            } else {
                if (outputApkFile.exists()) outputApkFile.delete()
                outputApkFile.parentFile?.mkdirs()
                tempUnsignedApk.copyTo(outputApkFile, overwrite = true)
            }

            onProgress(1.0f, R.string.status_merge_completed, 0)
            Timber.i("Merge successful: ${outputApkFile.absolutePath} (${outputApkFile.length()} bytes)")
            success = true
            Result.success(outputApkFile)
        } catch (t: Throwable) {
            Timber.e(t, "Merge pipeline failed")
            Result.failure(t)
        } finally {
            if (tempUnsignedApk.exists()) {
                tempUnsignedApk.delete()
            }
        }
    }

    private data class BaseProcessResult(
        val maxDex: Int,
        val originalApplicationClass: String?,
        val baseArscBytes: ByteArray?,
    )

    private fun processBaseApk(
        baseApkFile: File,
        writer: ApkZipWriter,
        existingEntries: MutableSet<String>,
        bypassSignature: Boolean = false,
    ): BaseProcessResult {
        var maxDex = 0
        var originalAppClass: String? = null
        var baseArscBytes: ByteArray? = null
        ZipFile(baseApkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                if (isSignatureFile(name)) {
                    continue // Strip obsolete signatures
                }

                if (name == "resources.arsc") {
                    baseArscBytes = zip.getInputStream(entry).use { it.readBytes() }
                    continue
                }

                if (name == "AndroidManifest.xml") {
                    val rawBytes = zip.getInputStream(entry).use { it.readBytes() }
                    var sanitizedBytes = sanitizeManifest(rawBytes)
                    if (bypassSignature) {
                        val patchResult = runCatching {
                            ManifestHookPatcher.patch(sanitizedBytes)
                        }.getOrNull()
                        if (patchResult != null) {
                            sanitizedBytes = patchResult.patchedManifestBytes
                            originalAppClass = patchResult.originalApplicationClass
                            Timber.i("Manifest patched for PMS Hook: originalApp=$originalAppClass")
                        } else {
                            Timber.w("Failed to patch Manifest for PMS Hook, proceeding with sanitized manifest")
                        }
                    }
                    writer.writeEntry(name, sanitizedBytes)
                    existingEntries.add(name)
                    continue
                }

                val dexMatch = dexRegex.find(name)
                if (dexMatch != null) {
                    val numStr = dexMatch.groupValues[1]
                    val dexNum = if (numStr.isEmpty()) 1 else numStr.toIntOrNull() ?: 1
                    if (dexNum > maxDex) maxDex = dexNum

                    existingEntries.add(name)
                    writer.copyEntry(zip, entry, name)
                    continue
                }

                existingEntries.add(name)
                writer.copyEntry(zip, entry, name)
            }
        }
        return BaseProcessResult(
            maxDex = maxDex.coerceAtLeast(1),
            originalApplicationClass = originalAppClass,
            baseArscBytes = baseArscBytes,
        )
    }

    private fun mergeSplitApk(
        splitFile: File,
        writer: ApkZipWriter,
        existingEntries: MutableSet<String>,
        currentDexIndex: Int,
        splitArscList: MutableList<ByteArray>,
    ): Int {
        var nextDex = currentDexIndex
        ZipFile(splitFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                // Ignore signatures and manifest from splits
                if (isSignatureFile(name) || name == "AndroidManifest.xml") {
                    continue
                }

                // Collect split resources.arsc table
                if (name == "resources.arsc") {
                    val splitArsc = zip.getInputStream(entry).use { it.readBytes() }
                    splitArscList.add(splitArsc)
                    continue
                }

                // Handle DEX renumbering
                if (dexRegex.matches(name)) {
                    nextDex++
                    val renumberedName = "classes$nextDex.dex"
                    existingEntries.add(renumberedName)
                    writer.copyEntry(zip, entry, renumberedName)
                    continue
                }

                // For all other files (lib/*.so, assets/*, res/*)
                if (name !in existingEntries) {
                    existingEntries.add(name)
                    writer.copyEntry(zip, entry, name)
                }
            }
        }
        return nextDex
    }

    /**
     * Sanitizes AndroidManifest.xml by prioritizing the high-performance Rust Native Core
     * with automatic fallback to KotlinManifestSanitizer if unavailable.
     */
    private fun sanitizeManifest(rawBytes: ByteArray): ByteArray {
        var processed = rawBytes
        if (RustAntiSplitBridge.isNativeEngineAvailable()) {
            val nativeResult = runCatching {
                RustAntiSplitBridge.nativeSanitizeManifest(processed)
            }
            if (nativeResult.isSuccess) {
                processed = nativeResult.getOrThrow()
                Timber.d("Manifest sanitized via Rust Native Core")
            } else {
                Timber.w(nativeResult.exceptionOrNull(), "Rust Native Core failed, falling back to Kotlin")
            }
        } else {
            Timber.d("Rust Native unavailable, using KotlinManifestSanitizer")
        }
        return KotlinManifestSanitizer.sanitize(processed)
    }


    private fun isSignatureFile(name: String): Boolean {
        if (!name.startsWith("META-INF/")) return false
        val upper = name.uppercase()
        return upper.endsWith(".SF") ||
                upper.endsWith(".RSA") ||
                upper.endsWith(".DSA") ||
                upper.endsWith(".EC") ||
                upper == "META-INF/MANIFEST.MF" ||
                upper.startsWith("META-INF/SIG-")
    }
}
