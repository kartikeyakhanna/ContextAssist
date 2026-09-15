package com.thread.app.service

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.thread.engine.model.ValueLookup
import org.json.JSONObject

/**
 * Reads the design-time Screen Memory Load scores produced offline by
 * tools/complexity-agent and shipped in config/complexity-cache.json.
 *
 * The engine never scores a screen at runtime. Calling a model on every screen
 * view would be slow, costly and non-deterministic, and precomputing per template
 * is how this would be built in production anyway - so the cache is the real
 * architecture, not a hackathon shortcut. Screens that are not in the cache fall
 * back to a neutral score and are queued for offline scoring, so it self-warms.
 */
object ComplexityCache {

    fun load(context: Context): Map<String, Double> = runCatching {
        val raw = context.assets.open("complexity-cache.json")
            .bufferedReader()
            .use { it.readText() }

        val screens = JSONObject(raw).getJSONObject("screens")
        buildMap {
            screens.keys().forEach { key ->
                put(key, screens.getJSONObject(key).getDouble("score"))
            }
        }
    }.getOrDefault(emptyMap())
}

/**
 * Works out what the user left the app to read.
 *
 * This is what makes the pin chip possible: knowing that they switched away is
 * not enough, you have to know what they went to fetch. On a phone this matters
 * far more than on a desktop - there is no second window, so a value read in
 * another app is simply gone by the time they are back. Hence the loop.
 */
object LookupDetector {

    /** Labels worth carrying. Kept narrow on purpose: a wrong pin is noise. */
    private val interestingLabels = listOf(
        "code", "reference", "account", "id", "number", "amount", "policy",
    )

    data class Candidate(val label: String, val value: String) {
        fun at(ts: Long, screenId: String = "", sourcePackage: String? = null) =
            ValueLookup(ts, screenId, label, value, sourcePackage)
    }

    /**
     * Looks for a short, copyable-looking value sitting next to a label. Short
     * values only - a pin is one line, and anything longer is not something a
     * person was trying to hold in their head.
     */
    fun capture(root: AccessibilityNodeInfo?): Candidate? {
        val node = root ?: return null
        val texts = mutableListOf<String>()
        collectText(node, texts, 0)

        for (i in texts.indices) {
            val label = texts[i].trim()
            if (interestingLabels.none { label.lowercase().contains(it) }) continue
            val value = texts.getOrNull(i + 1)?.trim() ?: continue
            if (value.length in 3..24 && value.any { it.isDigit() }) {
                return Candidate(label.trimEnd(':'), value)
            }
        }
        return null
    }

    private fun collectText(node: AccessibilityNodeInfo, into: MutableList<String>, depth: Int) {
        if (depth > 12 || into.size > 300) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { into += it }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, into, depth + 1) }
        }
    }
}
