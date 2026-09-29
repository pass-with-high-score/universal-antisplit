package app.pwhs.universalantisplit.data.scanner

import app.pwhs.universalantisplit.engine.RustAntiSplitBridge
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

data class IntegrityCheckResult(
    val hasV2Signature: Boolean = false,
    val hasV3Signature: Boolean = false,
    val isPairIpDetected: Boolean = false,
    val isNativeProtectionDetected: Boolean = false,
    val nativeProtectionLib: String? = null,
    val certFingerprint: String? = null,
    val isScanned: Boolean = false,
)

object IntegrityScanner {
    private val KNOWN_NATIVE_PROTECTIONS = mapOf(
        "libanort.so" to "Tencent Anti-Cheat Expert (ACE / MTP)",
        "libCrashSight.so" to "CrashSight Native Monitor",
        "libpairipcore.so" to "Google Play Automatic Integrity (PairIP)",
        "libwukong.so" to "Wukong Native Integrity Engine",
        "libmsaoaidsec.so" to "MSA Security Native Guard",
        "libsecshell.so" to "SecShell Binary Shield",
    )

    fun scanApkFiles(apkFiles: List<File>): IntegrityCheckResult {
        var isPairIp = false
        var nativeLibDetected: String? = null
        var hasV2 = false
        var hasV3 = false
        var certFingerprint: String? = null

        for (file in apkFiles) {
            if (!file.exists()) continue

            // 1. Scan zip entries for native protection libraries
            runCatching {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (name.contains("pairip", ignoreCase = true)) {
                            isPairIp = true
                        }
                        for ((libName, desc) in KNOWN_NATIVE_PROTECTIONS) {
                            if (name.endsWith(libName, ignoreCase = true)) {
                                if (libName == "libpairipcore.so") {
                                    isPairIp = true
                                } else if (nativeLibDetected == null) {
                                    nativeLibDetected = "$libName ($desc)"
                                }
                            }
                        }

                        if (!isPairIp && name.startsWith("classes") && name.endsWith(".dex")) {
                            zip.getInputStream(entry).use { inStream ->
                                if (containsPairIpBytecode(inStream)) {
                                    isPairIp = true
                                }
                            }
                        }
                    }
                }
            }.onFailure {
                Timber.w(it, "Failed to scan entries of %s", file.name)
            }

            // 2. Scan primary APK signature using Rust native verifier
            if (!hasV2 && !hasV3) {
                runCatching {
                    val jsonStr = RustAntiSplitBridge.nativeVerifyApkIntegrity(file.absolutePath)
                    if (!jsonStr.isNullOrBlank()) {
                        val json = JSONObject(jsonStr)
                        if (json.optBoolean("success", false)) {
                            hasV2 = json.optBoolean("hasV2", false)
                            hasV3 = json.optBoolean("hasV3", false)
                            val certsArray = json.optJSONArray("certificates")
                            if (certsArray != null && certsArray.length() > 0) {
                                certFingerprint = certsArray.optString(0)
                            }
                        }
                    }
                }.onFailure {
                    Timber.w(it, "Native integrity check failed for %s", file.name)
                }
            }
        }

        return IntegrityCheckResult(
            hasV2Signature = hasV2,
            hasV3Signature = hasV3,
            isPairIpDetected = isPairIp,
            isNativeProtectionDetected = nativeLibDetected != null,
            nativeProtectionLib = nativeLibDetected,
            certFingerprint = certFingerprint,
            isScanned = true,
        )
    }

    private val PAIRIP_NEEDLE = "com/pairip".toByteArray(java.nio.charset.StandardCharsets.US_ASCII)

    private fun containsPairIpBytecode(input: java.io.InputStream): Boolean {
        val buffer = ByteArray(64 * 1024)
        var overlap = 0
        while (true) {
            val bytesRead = input.read(buffer, overlap, buffer.size - overlap)
            if (bytesRead <= 0) break
            val total = overlap + bytesRead
            if (indexOf(buffer, total, PAIRIP_NEEDLE) >= 0) {
                return true
            }
            overlap = PAIRIP_NEEDLE.size - 1
            System.arraycopy(buffer, total - overlap, buffer, 0, overlap)
        }
        return false
    }

    private fun indexOf(source: ByteArray, sourceLength: Int, target: ByteArray): Int {
        if (target.isEmpty() || sourceLength < target.size) return -1
        val max = sourceLength - target.size
        for (i in 0..max) {
            var match = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }
}
