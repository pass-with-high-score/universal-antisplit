package app.pwhs.universalantisplit.engine.tracker

import android.content.Context
import org.json.JSONArray
import timber.log.Timber

data class TrackerEntry(
    val id: Int,
    val name: String,
    val code: String,
    val cats: List<String> = emptyList(),
)

/**
 * In-memory database of known trackers sourced from Exodus Privacy.
 * Each tracker has one or more `code_signature` patterns (Java package prefixes)
 * that can be matched against class names found inside DEX files.
 *
 * Source: https://reports.exodus-privacy.eu.org/api/trackers
 */
class TrackerDatabase(private val context: Context) {

    private var entries: List<TrackerEntry> = emptyList()
    private var codeSignatures: List<Pair<String, TrackerEntry>> = emptyList()

    /** Load tracker database from bundled asset. Safe to call multiple times. */
    fun load() {
        if (entries.isNotEmpty()) return
        try {
            val raw = context.assets.open("exodus_trackers.json")
                .bufferedReader()
                .use { it.readText() }
            val arr = JSONArray(raw)
            val list = mutableListOf<TrackerEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val cats = mutableListOf<String>()
                val catsArr = obj.optJSONArray("cats")
                if (catsArr != null) {
                    for (j in 0 until catsArr.length()) {
                        cats.add(catsArr.getString(j))
                    }
                }
                list.add(
                    TrackerEntry(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        code = obj.getString("code"),
                        cats = cats,
                    )
                )
            }
            entries = list
            codeSignatures = entries.flatMap { entry ->
                entry.code.split("|").map { sig ->
                    sig.trim() to entry
                }.filter { it.first.isNotEmpty() }
            }
            Timber.i("Loaded ${entries.size} trackers with ${codeSignatures.size} signatures")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load tracker database")
            entries = emptyList()
            codeSignatures = emptyList()
        }
    }

    /** Detect trackers present in a set of DEX class names. */
    fun detectTrackers(classNames: Set<String>): List<TrackerEntry> {
        load()
        val dotClassNames = classNames.map {
            it.replace('/', '.').removeSuffix(".class")
        }
        val detected = mutableSetOf<Int>()
        val result = mutableListOf<TrackerEntry>()

        for ((sig, entry) in codeSignatures) {
            if (entry.id in detected) continue
            val normalSig = sig.removeSuffix(".")
            if (dotClassNames.any { it.startsWith(normalSig) }) {
                detected.add(entry.id)
                result.add(entry)
            }
        }
        return result
    }

    /** Get all code signature prefixes for detected trackers. */
    fun getTrackerPackagePrefixes(detected: List<TrackerEntry>): Set<String> {
        return detected.flatMap { entry ->
            entry.code.split("|").map { sig ->
                sig.trim().removeSuffix(".").replace('.', '/')
            }.filter { it.isNotEmpty() }
        }.toSet()
    }

    fun getTotalCount(): Int {
        load()
        return entries.size
    }
}
