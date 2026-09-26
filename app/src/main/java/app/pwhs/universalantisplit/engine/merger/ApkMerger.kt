package app.pwhs.universalantisplit.engine.merger

import android.content.Context
import app.pwhs.universalantisplit.R
import app.pwhs.universalantisplit.engine.RustAntiSplitBridge
import app.pwhs.universalantisplit.engine.signer.ApkSignerManager
import app.pwhs.universalantisplit.engine.tracker.TrackerStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class MergeOptions(
    val useRustEngine: Boolean = true,
    val autoSign: Boolean = true,
    val stripTrackers: Boolean = false,
)

/**
 * High-performance APK Merger engine that unifies base APK and selected Split APKs
 * into a single standalone monolithic APK.
 */
class ApkMerger(
    private val context: Context,
    private val signerManager: ApkSignerManager = ApkSignerManager(context),
    private val trackerStripper: TrackerStripper? = null,
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
            require(baseApkFile.exists() && baseApkFile.length() > 0) {
                "Base APK does not exist or is empty: ${baseApkFile.absolutePath}"
            }

            onProgress(0.1f, R.string.status_analyzing_structure, splitFiles.size + 1)
            Timber.i("Beginning merge of base=${baseApkFile.name} with ${splitFiles.size} splits")

            val existingEntryNames = mutableSetOf<String>()
            var highestDexIndex = 1

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempUnsignedApk))).use { zos ->
                // Step 1: Process base.apk
                onProgress(0.25f, R.string.status_cleaning_manifest, 0)
                highestDexIndex = processBaseApk(
                    baseApkFile = baseApkFile,
                    zos = zos,
                    existingEntries = existingEntryNames,
                    useRustEngine = options.useRustEngine
                )

                // Step 2: Detect and strip trackers if enabled
                val detectedTrackers = if (options.stripTrackers && trackerStripper != null) {
                    onProgress(0.4f, R.string.status_stripping_trackers, 0)
                    trackerStripper.detectTrackersInApk(baseApkFile)
                } else {
                    emptyList()
                }
                val trackerPrefixes = if (detectedTrackers.isNotEmpty()) {
                    Timber.i("Detected ${detectedTrackers.size} trackers to strip")
                    trackerStripper?.getTrackerPackagePrefixes(detectedTrackers) ?: emptySet()
                } else {
                    emptySet()
                }

                // Step 3: Merge each split APK
                onProgress(0.5f, R.string.status_merging_resources, 0)
                var currentDexIndex = highestDexIndex
                for ((index, splitFile) in splitFiles.withIndex()) {
                    if (!splitFile.exists() || splitFile.length() == 0L) continue
                    val progressRatio = 0.5f + (0.3f * (index + 1) / splitFiles.size.coerceAtLeast(1))
                    onProgress(progressRatio, R.string.status_merging_resources, index + 1)

                    currentDexIndex = mergeSplitApk(
                        splitFile = splitFile,
                        zos = zos,
                        existingEntries = existingEntryNames,
                        currentDexIndex = currentDexIndex,
                        stripTrackerLibs = options.stripTrackers,
                    )
                }
            }

            // Step 4: Sign the APK or copy directly
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

    private fun processBaseApk(
        baseApkFile: File,
        zos: ZipOutputStream,
        existingEntries: MutableSet<String>,
        useRustEngine: Boolean,
    ): Int {
        var maxDex = 0
        ZipFile(baseApkFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                if (isSignatureFile(name)) {
                    continue // Strip obsolete signatures
                }

                if (name == "AndroidManifest.xml") {
                    val rawBytes = zip.getInputStream(entry).use { it.readBytes() }
                    var sanitizedBytes = sanitizeManifest(rawBytes, useRustEngine)
                    // Strip tracker manifest entries if trackerStripper is available
                    if (trackerStripper != null) {
                        val detected = trackerStripper.detectTrackersInApk(baseApkFile)
                        if (detected.isNotEmpty()) {
                            val (patched, count) = trackerStripper.neutralizeTrackerManifest(
                                sanitizedBytes, detected
                            )
                            sanitizedBytes = patched
                            Timber.i("Neutralized $count tracker entries in manifest")
                        }
                    }
                    writeZipEntry(zos, name, sanitizedBytes)
                    existingEntries.add(name)
                    continue
                }

                val dexMatch = dexRegex.find(name)
                if (dexMatch != null) {
                    val numStr = dexMatch.groupValues[1]
                    val dexNum = if (numStr.isEmpty()) 1 else numStr.toIntOrNull() ?: 1
                    if (dexNum > maxDex) maxDex = dexNum
                }

                existingEntries.add(name)
                zip.getInputStream(entry).use { input ->
                    val newEntry = ZipEntry(name)
                    zos.putNextEntry(newEntry)
                    input.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
        return maxDex.coerceAtLeast(1)
    }

    private fun mergeSplitApk(
        splitFile: File,
        zos: ZipOutputStream,
        existingEntries: MutableSet<String>,
        currentDexIndex: Int,
        stripTrackerLibs: Boolean = false,
    ): Int {
        var nextDex = currentDexIndex
        ZipFile(splitFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                // Ignore signatures, manifest and duplicate resources.arsc from splits
                if (isSignatureFile(name) || name == "AndroidManifest.xml" || name == "resources.arsc") {
                    continue
                }

                // Handle DEX renumbering
                if (dexRegex.matches(name)) {
                    nextDex++
                    val renumberedName = "classes$nextDex.dex"
                    existingEntries.add(renumberedName)
                    zip.getInputStream(entry).use { input ->
                        val newEntry = ZipEntry(renumberedName)
                        zos.putNextEntry(newEntry)
                        input.copyTo(zos)
                        zos.closeEntry()
                    }
                    continue
                }

                // For all other files (lib/*.so, assets/*, res/*)
                if (name !in existingEntries) {
                    // Skip tracker native libs if stripping enabled
                    if (stripTrackerLibs && trackerStripper != null && trackerStripper.isTrackerNativeLib(name)) {
                        Timber.d("Stripped tracker native lib: $name")
                        continue
                    }
                    existingEntries.add(name)
                    zip.getInputStream(entry).use { input ->
                        val newEntry = ZipEntry(name)
                        zos.putNextEntry(newEntry)
                        input.copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }
        }
        return nextDex
    }

    private fun sanitizeManifest(rawBytes: ByteArray, useRustEngine: Boolean): ByteArray {
        var processed = rawBytes
        if (useRustEngine && RustAntiSplitBridge.isNativeEngineAvailable()) {
            processed = runCatching {
                RustAntiSplitBridge.nativeSanitizeManifest(processed)
            }.getOrElse {
                Timber.w(it, "Native sanitize failed, continuing with raw bytes")
                rawBytes
            }
        }
        // Always run KotlinManifestSanitizer as final pass to guarantee
        // extractNativeLibs neutralization (prevents INSTALL_FAILED_INVALID_APK res=-2)
        return KotlinManifestSanitizer.sanitize(processed)
    }

    private fun writeZipEntry(zos: ZipOutputStream, entryName: String, data: ByteArray) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
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
