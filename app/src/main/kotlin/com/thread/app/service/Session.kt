package com.thread.app.service

import com.thread.engine.OfferComposer
import com.thread.engine.TaskStateBuilder

/**
 * One app's context. Everything Thread knows about what the user was doing there.
 *
 * Previously there was exactly one of these, held in fields on the service, which
 * meant the user could only ever have one thing on the go. That is not how a phone
 * is used: you are mid-claim, you check a budget code, you search for something,
 * you answer a message. Each of those is a separate thread to lose.
 */
class Session(
    val packageName: String,
    var builder: TaskStateBuilder,
    /**
     * True when an integrated app declared this task via the SDK.
     *
     * Declared sessions outrank inferred ones everywhere it matters: they are never
     * evicted to make room, never re-anchored away from, and only the app that
     * started one may end it.
     */
    var declared: Boolean,
    var lastSeenAt: Long,
) {
    var awayAt: Long? = null

    val postReturn = PostReturnWatcher()
    val orbit = OrbitTracker()

    /** Live Screen Memory Load, per screen of this app. */
    val liveSml = HashMap<String, Double>()

    /** Most recent text submitted from the resumption card, held in memory only. */
    var latestSubmittedText: String? = null

    /** True once there is something worth restoring, rather than just a presence. */
    fun hasContext(): Boolean =
        OfferComposer.resumption(builder.state, triggeredBy = null).hasContent
}

/**
 * The apps the user currently has something going on in.
 *
 * Bounded on purpose. "Every app you have ever opened" is a record of somebody's
 * day, and holding one would make Thread a surveillance tool that happens to be
 * helpful. Five is enough to cover a realistic interruption chain - claim, lookup,
 * search, message - and small enough that the whole thing stays comprehensible if
 * a user asks what is being held.
 */
class SessionStore(private val maxApps: Int = MAX_APPS) {

    // Access-ordered: iteration starts at the least recently touched.
    private val sessions = LinkedHashMap<String, Session>(8, 0.75f, true)

    fun get(packageName: String): Session? = sessions[packageName]

    fun all(): List<Session> = sessions.values.toList()

    fun put(session: Session) {
        sessions[session.packageName] = session
    }

    fun remove(packageName: String): Session? = sessions.remove(packageName)

    fun touch(packageName: String, now: Long) {
        sessions[packageName]?.lastSeenAt = now
    }

    /**
     * Drops the least recently used inferred sessions until the store is in budget.
     *
     * Declared sessions are never evicted. An app that said "the user is submitting
     * a travel claim" has made a commitment about what matters, and silently
     * discarding it because the user checked four other things would lose exactly
     * the context that was worth keeping.
     */
    fun evict(): List<String> {
        val dropped = mutableListOf<String>()
        while (sessions.size > maxApps) {
            val victim = sessions.values.firstOrNull { !it.declared } ?: break
            sessions.remove(victim.packageName)
            dropped += victim.packageName
        }
        return dropped
    }

    fun clear() = sessions.clear()

    companion object {
        const val MAX_APPS = 5
    }
}
