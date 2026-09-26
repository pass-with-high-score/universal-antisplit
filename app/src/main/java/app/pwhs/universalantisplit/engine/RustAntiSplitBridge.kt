package app.pwhs.universalantisplit.engine

import timber.log.Timber

/**
 * JNI Bridge interfacing Kotlin with the high-performance Rust Native NDK Core (`librust_antisplit.so`).
 */
object RustAntiSplitBridge {

    private var isLoaded: Boolean = false

    init {
        try {
            System.loadLibrary("rust_antisplit")
            isLoaded = true
            nativeInitLogger()
            Timber.i("Successfully loaded librust_antisplit.so")
        } catch (t: Throwable) {
            isLoaded = false
            Timber.w(t, "Failed to load librust_antisplit.so, falling back to Kotlin Engine")
        }
    }

    fun isNativeEngineAvailable(): Boolean = isLoaded

    // JNI Native methods
    private external fun nativeInitLogger()
    external fun nativeGetEngineVersion(): String
    external fun nativeSanitizeManifest(manifestBytes: ByteArray): ByteArray
    external fun nativeMergeSplits(baseApkPath: String, splitPaths: Array<String>, outputPath: String): Boolean
}
