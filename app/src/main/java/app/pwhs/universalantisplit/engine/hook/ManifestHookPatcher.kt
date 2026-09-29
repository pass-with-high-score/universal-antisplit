package app.pwhs.universalantisplit.engine.hook

import com.reandroid.app.AndroidManifest
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.ByteArrayInputStream

data class ManifestPatchResult(
    val patchedManifestBytes: ByteArray,
    val packageName: String?,
    val originalApplicationClass: String?,
)

object ManifestHookPatcher {
    const val HOOK_PROVIDER_CLASS = "app.pwhs.universalantisplit.hook.PmsHookProvider"
    const val HOOK_APPLICATION_CLASS = "app.pwhs.universalantisplit.hook.PmsHookApplication"

    fun patch(rawManifestBytes: ByteArray): ManifestPatchResult {
        val manifest = AndroidManifestBlock()
        manifest.readBytes(ByteArrayInputStream(rawManifestBytes))

        val packageName = manifest.packageName
        val app = manifest.applicationElement
            ?: throw IllegalStateException("Failed to locate <application> tag in AndroidManifest.xml")

        val originalAppClass = manifest.applicationClassName

        // Add PmsHookProvider under <application> with highest initialization priority (initOrder).
        // This keeps the original Application class (e.g. ShopeeApplication) 100% intact,
        // completely preventing ClassCastException while ensuring the PMS hook runs on app launch.
        val provider = app.createChildElement("provider")
        provider.getOrCreateAndroidAttribute(AndroidManifest.NAME_name, AndroidManifest.ID_name)
            .setValueAsString(HOOK_PROVIDER_CLASS)
        provider.getOrCreateAndroidAttribute(AndroidManifest.NAME_authorities, AndroidManifest.ID_authorities)
            .setValueAsString("$packageName.antisplit_pms_hook")
        provider.getOrCreateAndroidAttribute(AndroidManifest.NAME_exported, AndroidManifest.ID_exported)
            .setValueAsBoolean(false)
        provider.getOrCreateAndroidAttribute("initOrder", 0x0101001a)
            .setValueAsDecimal(1999999999)

        // Ensure split attributes on <manifest> and <application> are stripped
        manifest.manifestElement?.let { root ->
            listOf("isSplitRequired", "isFeatureSplit", "requiredSplitTypes", "splitTypes", "isolatedSplits", "configForSplit", "split").forEach { attrName ->
                root.searchAttributeByName(attrName)?.removeSelf()
            }
        }
        listOf("isSplitRequired", "isFeatureSplit", "requiredSplitTypes", "splitTypes", "isolatedSplits", "configForSplit").forEach { attrName ->
            app.searchAttributeByName(attrName)?.removeSelf()
        }

        manifest.refresh()
        val patchedBytes = manifest.bytes

        return ManifestPatchResult(
            patchedManifestBytes = patchedBytes,
            packageName = packageName,
            originalApplicationClass = originalAppClass,
        )
    }
}
