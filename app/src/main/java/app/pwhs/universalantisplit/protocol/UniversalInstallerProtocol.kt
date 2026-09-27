package app.pwhs.universalantisplit.protocol

/**
 * Universal Installer Integration Protocol.
 * Enables 1-click invocation from Universal Installer (or any companion app)
 * to Universal Anti-Split and structured return of the merged APK.
 */
object UniversalInstallerProtocol {
    /**
     * Explicit intent action for requesting split APK merging.
     */
    const val ACTION_MERGE_SPLIT = "app.pwhs.universalinstaller.action.MERGE_SPLIT"

    // --- Input Extras ---
    /**
     * Optional target installed package name to extract and merge.
     */
    const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"

    /**
     * Optional boolean indicating if merge should start immediately upon loading.
     */
    const val EXTRA_AUTO_START = "EXTRA_AUTO_START"

    /**
     * Optional boolean indicating if the output APK should be automatically signed.
     */
    const val EXTRA_AUTO_SIGN = "EXTRA_AUTO_SIGN"

    // --- Output Result Extras ---
    /**
     * Absolute path of the output merged APK.
     */
    const val EXTRA_OUTPUT_PATH = "EXTRA_OUTPUT_PATH"

    /**
     * String representation of the content URI for the merged APK.
     */
    const val EXTRA_OUTPUT_URI = "EXTRA_OUTPUT_URI"

    /**
     * Package name of the merged application.
     */
    const val EXTRA_RESULT_PACKAGE = "EXTRA_RESULT_PACKAGE"

    /**
     * Boolean indicating if the merge operation succeeded.
     */
    const val EXTRA_SUCCESS = "EXTRA_SUCCESS"

    /**
     * Error message if the operation failed.
     */
    const val EXTRA_ERROR_MESSAGE = "EXTRA_ERROR_MESSAGE"
}
