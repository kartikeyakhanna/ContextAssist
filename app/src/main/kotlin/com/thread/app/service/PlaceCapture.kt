package com.thread.app.service

import android.view.accessibility.AccessibilityNodeInfo
import com.thread.engine.TextPlace
import com.thread.engine.model.DocumentPlace

/**
 * Reads where the cursor is sitting in a document, and nothing else.
 *
 * Separate from [SelectionCapture] because the two answer different questions.
 * A selection is text the user deliberately highlighted, usually to act on it.
 * A cursor is where they were *writing*, and it exists even when nothing is
 * selected - which is the normal case for someone interrupted mid-sentence, and
 * therefore the case this product exists to serve.
 *
 * Gated on [OfficeApps.exposesDocumentText] rather than applied everywhere. The
 * measurement that motivated this is that Word and Excel on Android render their
 * documents to a GPU canvas and expose no text at all, so there is nothing to read
 * there however hard Thread looks. Reaching into every editable field in every app
 * to pull out prose would not fix that, and would quietly turn a memory aid into
 * something that reads what people write.
 */
internal object PlaceCapture {

    private const val MAX_NODES = 200

    /**
     * The last text seen per app, so an edit can be located by what changed.
     *
     * Bounded by the number of apps that expose document text, which is currently
     * one. Cleared with the session rather than allowed to accumulate.
     */
    private val lastSeen = mutableMapOf<String, String>()

    fun forget(pkg: String) {
        lastSeen.remove(pkg)
    }

    fun clear() {
        lastSeen.clear()
    }

    /**
     * Finds the document in a window and reports where the cursor sits in it.
     *
     * Walks the host window rather than taking the input-focused node, because
     * measured on device the demo editor's Compose text field is not returned by
     * findFocus(FOCUS_INPUT) at all - the same class of gap that made
     * [TextCapture.onFocusedNode] necessary. The editable node carrying the most
     * text is the document; on a screen with several fields that is still the right
     * answer, because a document is by some distance the longest thing on it.
     */
    fun fromRoot(
        root: AccessibilityNodeInfo?,
        pkg: String,
        documentName: String?,
        now: Long,
    ): DocumentPlace? {
        val document = documentIn(root) ?: return null

        // The reported cursor is preferred where it exists, but Compose text fields
        // return -1 here while still exposing their contents, so the fallback is not
        // an edge case - it is the only path that works in the app this was built for.
        val cursor = document.cursor
            ?: TextPlace.cursorAfterEdit(lastSeen[pkg], document.text)

        lastSeen[pkg] = document.text
        if (cursor == null) return null

        return TextPlace.at(
            text = document.text,
            cursor = cursor,
            documentName = documentName,
            updatedAt = now,
        )
    }

    /**
     * Notes the document's contents without claiming to know where anyone was.
     *
     * Called when a document app comes into view, so that the first edit afterwards
     * has something to be compared against.
     */
    fun seed(root: AccessibilityNodeInfo?, pkg: String) {
        documentIn(root)?.let { lastSeen[pkg] = it.text }
    }

    /**
     * Why the on-screen position of a line is not read from here.
     *
     * The platform has an API for exactly that - refreshWithExtraData with
     * EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY - and the demo editor's field even
     * advertises support for it. Measured on a Pixel 10 it is not usable: it
     * returns bounds only for roughly the first 140 characters and nothing beyond,
     * and with the document scrolled to its end it still reported the coordinates
     * of the first paragraph, which was no longer on screen. The field scrolls
     * internally and those coordinates do not account for it.
     *
     * A highlight drawn from that would sit over whatever text happened to occupy
     * the position instead - the same failure the ring's own comment warns about,
     * and worse here because the user cannot easily check. Screen positions are
     * therefore taken only from apps that report them through the SDK.
     */
    private class Document(
        val node: AccessibilityNodeInfo,
        val text: String,
        val cursor: Int?,
    )

    private fun documentIn(root: AccessibilityNodeInfo?): Document? {
        val start = root ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(start)

        var best: AccessibilityNodeInfo? = null
        var bestLength = 0
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++

            if (node.isEditable && !node.isPassword) {
                val length = node.text?.length ?: 0
                if (length > bestLength) {
                    best = node
                    bestLength = length
                }
            }

            for (i in 0 until node.childCount) {
                runCatching { node.getChild(i) }.getOrNull()?.let(queue::add)
            }
        }

        val document = best ?: return null
        val text = document.text?.toString().orEmpty()
        if (text.isEmpty()) return null

        return Document(document, text, document.textSelectionStart.takeIf { it >= 0 })
    }

    fun fromNode(node: AccessibilityNodeInfo?, documentName: String?, now: Long): DocumentPlace? {
        val source = node ?: return null
        if (source.isPassword || !source.isEditable) return null

        val text = source.text?.toString().orEmpty()
        if (text.isEmpty()) return null

        // A collapsed selection is the cursor; an expanded one still has an anchor
        // worth using, so the start is taken in both cases.
        val cursor = source.textSelectionStart
        if (cursor < 0) return null

        return TextPlace.at(
            text = text,
            cursor = cursor,
            documentName = documentName,
            updatedAt = now,
        )
    }
}
