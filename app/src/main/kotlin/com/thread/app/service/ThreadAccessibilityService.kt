package com.thread.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.thread.app.overlay.OverlayController
import com.thread.engine.Arbiter
import com.thread.engine.OfferComposer
import com.thread.engine.TaskStateBuilder
import com.thread.engine.Triggers
import com.thread.engine.Weights
import com.thread.engine.model.AppSwitchAway
import com.thread.engine.model.AppSwitchReturn
import com.thread.engine.model.ScreenView
import com.thread.engine.model.ScrollReversal
import com.thread.engine.model.ThreadEvent
import com.thread.engine.scores.Cls
import com.thread.engine.scores.DsOrbit
import com.thread.engine.scores.LiveFacts
import com.thread.engine.scores.Sml
import java.util.UUID

/**
 * The collector. Tier 1: observes any app on the device with no integration at all.
 *
 * Why this exists rather than an Office add-in: Office Add-ins (Office.js) do not
 * run on Excel for Android, so there is no in-app surface to inject into. The
 * accessibility layer turns that constraint into the stronger position - Thread
 * works on Excel, Teams, Outlook and everything else without any of them changing
 * a line of code.
 *
 * Nothing observed here is persisted or transmitted. The engine is a pure-Kotlin
 * module with no network dependency, and there is no database in this repository.
 */
class ThreadAccessibilityService : AccessibilityService() {

    private val weights = Weights.DEFAULT
    private val arbiter = Arbiter(weights)
    private lateinit var overlay: OverlayController

    /** Design-time Screen Memory Load, loaded from config/complexity-cache.json. */
    private var complexityCache: Map<String, Double> = emptyMap()

    private var builder: TaskStateBuilder? = null
    private var observedPackage: String? = null
    private var awayAt: Long? = null

    /**
     * True when the task was inferred from the user opening an app, rather than
     * declared by an integrated one.
     *
     * The distinction is not cosmetic. An implicit session knows the user is in an
     * app and can see when they leave it and come back; it does not know what they
     * are trying to achieve, and it must not pretend to. A Tier 2 task always
     * wins - see [startTask].
     */
    private var implicitTask = false

    /** Live Screen Memory Load for screens absent from the design-time cache. */
    private val liveSml = HashMap<String, Double>()

    private val postReturn = PostReturnWatcher()
    private val orbit = OrbitTracker()

    private val main = Handler(Looper.getMainLooper())
    private var sdkReceiver: SdkEventReceiver? = null

    /** Resolved once the service connects; see [isSystemSurface]. */
    private var imePackage: String? = null

    private val alwaysIgnoredPackages = setOf(
        "com.android.systemui",
        "android",
    )

    /**
     * How long to wait after the user comes back before deciding anything.
     *
     * Evaluating on the first frame would be guessing - at that instant someone
     * who is fine and someone who is lost look identical. Waiting a few seconds
     * is what makes the difference observable: by then one of them has typed.
     */
    private val firstLookMs = 3_000L
    private val secondLookMs = 9_000L

    /**
     * How long the user must be in a different app before an implicit session
     * follows them to it.
     *
     * Below this, leaving is an interruption and they are coming back. Above it,
     * they have moved on, and continuing to hold the first app's context would be
     * both useless and a small betrayal of "we drop it the moment it stops being
     * yours". Never applies to a Tier 2 task: an app that declared a task is the
     * only thing that can end it.
     */
    private val reanchorMs = 120_000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        complexityCache = ComplexityCache.load(this)
        refreshImePackage()
        registerSdkReceiver()
    }

    /**
     * Tier 2 input. Exported because the broadcasts arrive from other apps; the
     * receiver only ever *accepts* events and never returns anything, so an app
     * can contribute to its own user's context and read nothing back.
     */
    private fun registerSdkReceiver() {
        val receiver = SdkEventReceiver(
            onEvent = { event -> applyEvent(event) },
            onTaskStart = { intent, pkg, screen -> startTask(intent, pkg, screen) },
            onTaskEnd = { endTask() },
        )
        sdkReceiver = receiver

        val filter = IntentFilter(SdkEventReceiver.ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val now = System.currentTimeMillis()
        val pkg = e.packageName?.toString() ?: return

        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChanged(pkg, e, now)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(e, now)

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                postReturn.onFocus(now)
                orbit.onInteraction(now)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            -> {
                postReturn.onProductiveAction(now)
                orbit.onCommit(now)
            }
        }
    }

    /**
     * Interruption and return detection.
     *
     * This path needs only the package name - no node tree at all. That is why
     * the hero scenario cannot be broken by an app that renders its content on a
     * custom canvas, which is exactly what Excel's grid does.
     */
    private fun onWindowChanged(pkg: String, e: AccessibilityEvent, now: Long) {
        // The keyboard, the status bar and our own overlay are all separate
        // packages, and all three raise window-state changes. Treating them as
        // app switches would mean every text field the user touches registers as
        // an interruption and every keyboard dismissal as a disoriented return -
        // which on a phone is constant, and would bury the real signal entirely.
        if (isSystemSurface(pkg)) return

        val tracked = observedPackage
        if (tracked == null) {
            // Tier 1. No app told us anything; the user opened something, and that
            // is enough to start watching for them leaving it and coming back.
            beginImplicitTask(pkg, screenIdOf(pkg, e))
            return
        }

        val currentScreen = builder?.state?.currentScreenId ?: screenIdOf(pkg, e)

        if (pkg != tracked) {
            if (awayAt == null) {
                awayAt = now
                Log.d(TAG, "away -> $pkg")
                applyEvent(AppSwitchAway(now, currentScreen, pkg))
            } else if (implicitTask && now - (awayAt ?: now) > reanchorMs) {
                Log.d(TAG, "re-anchoring: ${reanchorMs / 1000}s in $pkg, they have moved on")
                beginImplicitTask(pkg, screenIdOf(pkg, e))
                return
            }
            // Capture what they went to read, so it can be pinned when they get
            // back. Re-checked on every screen in the other app, because the
            // value is usually a tap or two in, not on the landing screen.
            LookupDetector.capture(rootInActiveWindow)?.let {
                applyEvent(it.at(now, currentScreen, pkg))
            }
            return
        }

        val screenId = screenIdOf(pkg, e)

        if (awayAt != null) {
            val awaySeconds = (now - (awayAt ?: now)) / 1000
            awayAt = null
            Log.d(TAG, "return after ${awaySeconds}s onto $screenId")
            postReturn.begin(now)
            applyEvent(AppSwitchReturn(now, screenId, null))
            scheduleReturnEvaluation()
            return
        }

        applyEvent(ScreenView(now, screenId))
        orbit.onScreen(screenId, now)
        scheduleOrbitEvaluation()
    }

    /**
     * Two looks, not one.
     *
     * The first catches someone who came back and stalled immediately. The second
     * catches the more common case: they start, realise they cannot remember where
     * they had got to, and stall a beat later. A single check would miss whichever
     * case it was not timed for.
     */
    private fun scheduleReturnEvaluation() {
        main.removeCallbacksAndMessages(RETURN_TOKEN)
        listOf(firstLookMs, secondLookMs).forEach { delay ->
            main.postAtTime(
                { evaluateAfterReturn(System.currentTimeMillis()) },
                RETURN_TOKEN,
                SystemClock.uptimeMillis() + delay,
            )
        }
    }

    /** Debounced: orbiting is a pattern over time, not a single navigation. */
    private fun scheduleOrbitEvaluation() {
        main.removeCallbacksAndMessages(ORBIT_TOKEN)
        main.postAtTime(
            { evaluateOrbit(System.currentTimeMillis()) },
            ORBIT_TOKEN,
            SystemClock.uptimeMillis() + 1_500L,
        )
    }

    private fun onScrolled(e: AccessibilityEvent, now: Long) {
        // Note: Jetpack Compose surfaces emit no TYPE_VIEW_SCROLLED at all, so this
        // never runs on a Compose UI. Verified on device; see docs/scoring.md.
        val down = scrollingDown(e)
        if (down == null) {
            Log.d(TAG, "scroll ignored - no usable delta (deltaY=${runCatching { e.scrollDeltaY }.getOrNull()})")
            return
        }
        if (!orbit.isReversal(down, now)) return

        Log.d(TAG, "scroll reversal (now heading ${if (down) "down" else "up"})")
        val screen = builder?.state?.currentScreenId ?: return
        applyEvent(ScrollReversal(now, screen))
        postReturn.onScrollReversal()
    }

    /**
     * Scroll direction, with a deliberate refusal to guess.
     *
     * Reversals are the signal; continuous scrolling in one direction is reading,
     * which is not struggle. Where the platform reports no usable delta the event
     * is dropped rather than counted, because a false reversal inflates the orbit
     * score, and an inflated score interrupts someone who was doing fine.
     */
    private fun scrollingDown(e: AccessibilityEvent): Boolean? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val delta = e.scrollDeltaY
            if (delta != 0 && delta != Int.MIN_VALUE) return delta > 0
        }
        return null
    }

    /**
     * Screen Memory Load, in order of trustworthiness.
     *
     * 1. The design-time cache, if this screen was scored offline. A model read the
     *    whole template and could reason about what the words mean.
     * 2. A live measurement of the tree in front of us, computed once per screen.
     * 3. [Sml.NEUTRAL], when the app exposes nothing to walk.
     *
     * The order matters: where the cache and the live count disagree, the cache is
     * the better measurement and wins. Live scoring exists to stop unknown screens
     * being scored as average, not to second-guess the scored ones.
     */
    private fun smlFor(screenId: String): Double {
        complexityCache[screenId]?.let { return it }
        liveSml[screenId]?.let { return it }

        val nodes = LiveComplexity.flatten(rootInActiveWindow)
        if (nodes.isEmpty()) {
            Log.d(TAG, "sml: $screenId - nothing readable, using neutral")
            return Sml.NEUTRAL
        }

        val facts = LiveFacts.facts(screenId, nodes)
        val value = Sml.score(facts, weights).value
        liveSml[screenId] = value

        Log.d(
            TAG,
            "sml(live): $screenId = ${"%.1f".format(value)} " +
                "nodes=${nodes.size} options=${facts.optionCount} fields=${facts.itemsToHold} " +
                "irreversible=${facts.irreversibleActions} progressVisible=${facts.progressVisible}",
        )
        return value
    }

    /**
     * Runs shortly after the user comes back, once there is enough evidence to
     * tell reorientation from ordinary resumption.
     */
    fun evaluateAfterReturn(now: Long) {
        val state = builder?.state ?: return
        val sml = smlFor(state.currentScreenId)
        val cls = Cls.score(state, postReturn.signals(now), sml, weights)

        Log.d(TAG, "afterReturn: sml=$sml cls=${cls.value.toInt()} factors=${cls.factors}")

        val evaluation = Triggers.evaluate(
            state = state,
            cls = cls,
            orbit = DsOrbit.score(state, orbit.signals(now), weights),
            justReturned = true,
            w = weights,
        )

        present(evaluation, now)
    }

    /**
     * The other half. Someone who never left the app can still be lost inside it -
     * going back and forth to a screen because they cannot hold what is on it.
     */
    fun evaluateOrbit(now: Long) {
        val state = builder?.state ?: return
        val sml = smlFor(state.currentScreenId)

        val evaluation = Triggers.evaluate(
            state = state,
            cls = Cls.score(state, postReturn.signals(now), sml, weights),
            orbit = DsOrbit.score(state, orbit.signals(now), weights),
            justReturned = false,
            w = weights,
        )

        present(evaluation, now)
    }

    private fun present(evaluation: Triggers.Evaluation, now: Long) {
        val selected = arbiter.select(evaluation.candidates, now)

        // The whole decision, in one line, on the operator's screen and never on
        // the user's. Every score carries its factor breakdown, so "why did it
        // fire?" is answerable after the fact rather than argued about.
        Log.d(
            TAG,
            "evaluate: candidates=${evaluation.candidates.map { it.kind }} " +
                "passiveOnly=${evaluation.passiveOnly} selected=${selected?.kind ?: "none"}",
        )

        when {
            selected != null -> overlay.show(selected, arbiter)
            evaluation.passiveOnly -> overlay.showPassiveMarker()
            else -> overlay.hideCardsOnly()
        }
    }

    /**
     * The dot was tapped. Always answered - no threshold, no cooldown, no
     * suppression check.
     *
     * This is the primary path, not a secondary one. Detection will sometimes be
     * wrong, and for someone already depleted a mistimed interruption is the very
     * harm this is meant to prevent. Asking requires one undifferentiated tap:
     * no recall, no phrasing, no deciding what to ask for.
     */
    fun onDotTapped(now: Long) {
        val state = builder?.state ?: return
        val offer = OfferComposer.resumption(state, triggeredBy = null)
        overlay.show(arbiter.userRequested(offer, now), arbiter)
    }

    private fun applyEvent(event: ThreadEvent) {
        builder?.apply(event)
    }

    /**
     * A declared task. Tier 2, and it always outranks an inferred session.
     *
     * Inferring task *boundaries* from the node tree is possible but unreliable,
     * and the cost of getting it wrong is asymmetric: a task that starts late
     * loses the very decisions worth restoring, and one that never ends holds
     * context it has no business holding. So an integrated app states them.
     *
     * Without integration Thread still runs - see [beginImplicitTask] - it just
     * observes behaviour instead of restoring content.
     */
    fun startTask(intent: String, packageName: String, screenId: String) {
        Log.d(TAG, "startTask: '$intent' pkg=$packageName screen=$screenId")
        implicitTask = false
        beginTask(intent, packageName, screenId)

        // The dot appears only while a task is open, and it is the only thing
        // Thread ever shows unprompted.
        overlay.showDot { onDotTapped(System.currentTimeMillis()) }
    }

    /**
     * Tier 1 session. Starts because an app came to the foreground, nothing more.
     *
     * What this buys: interruption and return detection, screen complexity, orbit
     * detection - everything that comes from watching behaviour. What it cannot
     * buy is intent, so the task is named after the app and the card says only
     * what was actually observed. The alternative, inventing a plausible-sounding
     * goal, would put words in the user's mouth at the exact moment they are least
     * able to tell that they are wrong.
     *
     * No dot. An implicit session has no restored context worth pulling, so
     * offering a way to pull it would be a lie. It becomes visible only if the
     * user's own behaviour says they are struggling.
     */
    private fun beginImplicitTask(packageName: String, screenId: String) {
        Log.d(TAG, "implicit session: pkg=$packageName screen=$screenId")
        implicitTask = true
        beginTask(appLabel(packageName), packageName, screenId)
        if (::overlay.isInitialized) overlay.hide()
    }

    private fun beginTask(intent: String, packageName: String, screenId: String) {
        observedPackage = packageName
        awayAt = null
        orbit.reset()
        postReturn.clear()
        liveSml.clear()

        builder = TaskStateBuilder(
            taskId = UUID.randomUUID().toString(),
            intent = intent,
            startScreenId = screenId,
            startedAt = System.currentTimeMillis(),
        )
    }

    /** "what you were doing in Excel" reads better than a package name. */
    private fun appLabel(packageName: String): String {
        val label = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrNull() ?: packageName.substringAfterLast('.')

        return "what you were doing in $label"
    }

    /** Task over. Everything about it is dropped, immediately and completely. */
    fun endTask() {
        Log.d(TAG, "endTask - dropping all task state")
        main.removeCallbacksAndMessages(RETURN_TOKEN)
        main.removeCallbacksAndMessages(ORBIT_TOKEN)
        builder = null
        observedPackage = null
        implicitTask = false
        awayAt = null
        orbit.reset()
        postReturn.clear()
        liveSml.clear()
        if (::overlay.isInitialized) overlay.hide()
    }

    /**
     * The platform asking us to stop whatever feedback is in flight.
     *
     * Note what this does NOT do: end the task or remove the dot. The system
     * calls this for its own reasons - it means "be quiet now", not "the user is
     * finished". Tearing down the task here would silently discard the context
     * the user is relying on, and the dot would vanish at the exact moment they
     * might reach for it.
     */
    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt - clearing offers, keeping task and dot")
        if (::overlay.isInitialized) overlay.hideCardsOnly()
    }

    override fun onDestroy() {
        endTask()
        sdkReceiver?.let { runCatching { unregisterReceiver(it) } }
        sdkReceiver = null
        super.onDestroy()
    }

    private fun screenIdOf(pkg: String, e: AccessibilityEvent): String {
        val cls = e.className?.toString()?.substringAfterLast('.') ?: "unknown"
        return "$pkg/$cls"
    }

    /**
     * Surfaces that are technically other packages but are not somewhere the
     * user "went".
     *
     * The input method is the important one. It is a separate package, it raises
     * a window-state change every time a field is focused, and on a phone that
     * happens constantly - so without this check a form with six fields would
     * look like six interruptions and six disoriented returns.
     */
    private fun isSystemSurface(pkg: String): Boolean =
        pkg == packageName ||
            pkg in alwaysIgnoredPackages ||
            pkg == imePackage

    private fun refreshImePackage() {
        imePackage = runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
        }.getOrNull()
        Log.d(TAG, "ignoring IME package: $imePackage")
    }

    private companion object {
        const val TAG = "Thread"
        val RETURN_TOKEN = Any()
        val ORBIT_TOKEN = Any()
    }
}
