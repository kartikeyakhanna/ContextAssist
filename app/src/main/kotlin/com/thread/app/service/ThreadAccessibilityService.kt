package com.thread.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.thread.app.overlay.OverlayController
import com.thread.app.document.DocumentImportActivity
import com.thread.app.tools.BreakdownClient
import com.thread.app.tools.BreakdownClientException
import com.thread.app.tools.BreakdownContext
import com.thread.app.tools.ToolExecutionState
import com.thread.app.tools.ToolResult
import com.thread.engine.Arbiter
import com.thread.engine.OfferComposer
import com.thread.engine.Sequencer
import com.thread.engine.TaskStateBuilder
import com.thread.engine.Triggers
import com.thread.engine.model.Offer
import com.thread.engine.Weights
import com.thread.engine.model.AppSwitchAway
import com.thread.engine.model.AppSwitchReturn
import com.thread.engine.model.FieldCommit
import com.thread.engine.model.ScreenView
import com.thread.engine.model.ScrollReversal
import com.thread.engine.model.ThreadEvent
import com.thread.engine.scores.Cls
import com.thread.engine.scores.DsFreeze
import com.thread.engine.scores.DsOrbit
import com.thread.engine.scores.LiveFacts
import com.thread.engine.scores.Sml
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The collector. Tier 1: observes any app on the device with no integration at all.
 *
 * Why this exists rather than an Office add-in: Office Add-ins (Office.js) do not
 * run on Excel for Android, so there is no in-app surface to inject into. The
 * accessibility layer turns that constraint into the stronger position - Thread
 * works on Excel, Teams, Outlook and everything else without any of them changing
 * a line of code.
 *
 * Nothing observed here is persisted. Behavioural signals stay on-device. When
 * the user explicitly invokes @breakdown, the submitted task, the session's
 * intent label, and - only with the separate screen-context consent - the active
 * app, a bounded preview of visible labels, and any exposed text selection are
 * sent to the configured breakdown service.
 */
class ThreadAccessibilityService : AccessibilityService() {

    private val weights = Weights.DEFAULT
    private val arbiter = Arbiter(weights)
    private lateinit var overlay: OverlayController

    /** Design-time Screen Memory Load, loaded from config/complexity-cache.json. */
    private var complexityCache: Map<String, Double> = emptyMap()

    private val sessions = SessionStore()
    private lateinit var breakdownClient: BreakdownClient
    private val toolScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextToolRequestId = AtomicLong()

    /** The app in front of the user right now. */
    private var currentPackage: String? = null

    /** The app the card currently on screen was built to describe. */
    private var cardPackage: String? = null

    /** Live Screen Memory Load is now held per session; see [Session.liveSml]. */

    private val textCapture = TextCapture { entry -> onTextCommitted(entry) }

    private val main = Handler(Looper.getMainLooper())
    private var sdkReceiver: SdkEventReceiver? = null
    private var documentReceiver: BroadcastReceiver? = null
    private var probeReceiver: BroadcastReceiver? = null

    /** Resolved once the service connects; see [isSystemSurface]. */
    private var imePackage: String? = null
    private var launcherPackages: Set<String> = emptySet()

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
     * When to look for a choice freeze.
     *
     * Longer than the return looks, and necessarily so: being still is only
     * evidence once enough time has passed that being still is unusual. Twelve
     * seconds is under half the assumed baseline for a screen, so a normal read
     * does not reach the second look with a high score.
     */
    private val freezeFirstLookMs = 12_000L

    /** How often to look again while the user stays put. */
    private val freezeRecheckMs = 15_000L

    /** After this long on one screen, stop watching. */
    private val freezeWatchWindowMs = 240_000L

    /**
     * How many apps Thread will hold context for, and why there is a limit at all.
     *
     * Not a memory constraint - these are small. It is that context the user has
     * not touched in a long time is context they have moved on from, and holding
     * it indefinitely would quietly turn an assistive feature into a log of
     * everything they did today. See [SessionStore.evict].
     */
    private val heldApps = SessionStore.MAX_APPS

    /** Slightly longer than the capture debounce, so the last keystroke lands. */
    private val textFlushMs = 1_500L

    /** Minimum gap between node-tree reads for the focused field. */
    private val focusReadMs = 350L
    private var lastFocusReadAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        breakdownClient = BreakdownClient(this)
        complexityCache = ComplexityCache.load(this)
        refreshImePackage()
        resolveLauncherPackages()
        registerSdkReceiver()
        registerDocumentReceiver()

        // Present from the moment the service is on, not from the first app the
        // user happens to open. Nothing is held yet, which is the point: the way
        // in has to exist before there is a reason to use it.
        overlay.showDot { onDotTapped(System.currentTimeMillis()) }
    }

    /**
     * Tier 2 input. Exported because the broadcasts arrive from other apps; the
     * receiver only ever *accepts* events and never returns anything, so an app
     * can contribute to its own user's context and read nothing back.
     */
    private fun registerSdkReceiver() {
        val receiver = SdkEventReceiver(
            onEvent = { event -> applyDeclaredEvent(event) },
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

        if (PROBE_DUMP) registerProbeReceiver()
    }

    /**
     * Measurement aid. Dumps the tree on demand rather than on an event, because
     * the question being asked - does an app populate its canvas only when a
     * screen reader is attached - has to be asked with a screen reader running,
     * and that is exactly the situation where driving the device by touch, or by
     * uiautomator, stops being reliable. A broadcast needs neither.
     */
    private fun registerProbeReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val pkg = intent?.getStringExtra("pkg")
                val root = if (pkg != null) hostWindowRoot(pkg) else rootInActiveWindow
                val summary = NodeTreeProbe.summarise(root)
                Log.i("ThreadProbe", "on-demand dump pkg=${pkg ?: "active"} $summary")
                NodeTreeProbe.dump(root)
            }
        }
        probeReceiver = receiver

        val filter = IntentFilter(PROBE_DUMP_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    private fun registerDocumentReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val event = intent ?: return
                val name = event.getStringExtra(DocumentImportActivity.EXTRA_DOCUMENT_NAME)
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                val text = event.getStringExtra(DocumentImportActivity.EXTRA_DOCUMENT_TEXT)
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                val pkg = event.getStringExtra(DocumentImportActivity.EXTRA_TARGET_PACKAGE)
                    ?.takeIf(OfficeApps::isWord)
                    ?: return
                val now = System.currentTimeMillis()
                val session = sessions.get(pkg) ?: openSession(pkg, "$pkg/imported", now)
                session.attachDocument(name, text)
                if (overlay.hasUserRequestedCard() && cardPackage == pkg) {
                    onDotTapped(now)
                }
            }
        }
        documentReceiver = receiver
        val filter = IntentFilter(DocumentImportActivity.ACTION_DOCUMENT_IMPORTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val now = System.currentTimeMillis()
        val pkg = e.packageName?.toString() ?: return

        // Measurement aid, off by default. Answers what an app volunteers, which
        // is a different question from what it exposes when read - so the tree is
        // dumped alongside, on arrival at a screen.
        if (PROBE_EVENTS && !isSystemSurface(pkg)) {
            NodeTreeProbe.logEvent(e, pkg)
            if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                NodeTreeProbe.dump(hostWindowRoot(pkg))
            }
        }

        textCapture.flushIdle(now)

        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                onWindowChanged(pkg, e, now)
                // Records what the document looked like before anything was typed.
                // Without this the first edit after Thread starts has nothing to be
                // compared against and silently produces no place - which is exactly
                // the moment someone would be judging whether the thing works.
                if (OfficeApps.exposesDocumentText(pkg) && SensitiveApps.readContent(pkg)) {
                    PlaceCapture.seed(hostWindowRoot(pkg), pkg)
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // The ring is drawn at fixed screen coordinates, so anything that
                // moves content under it invalidates it immediately.
                if (!isSystemSurface(pkg)) clearRing()
                onScrolled(pkg, e, now)
            }

            // Fires constantly, and is the only signal some apps give that the
            // user is typing. Throttled rather than handled on every one.
            //
            // System surfaces are excluded because the keyboard emits these while
            // the user types and the throttle is shared: measured on device, the
            // IME consumed every read window and the app being typed into never
            // got one. The capture looked implemented and captured nothing.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                if (!isSystemSurface(pkg) && SensitiveApps.readContent(pkg)) {
                    captureFocusedField(pkg, now)
                }

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> sessions.get(pkg)?.let {
                it.postReturn.onFocus(now)
                it.orbit.onInteraction(now)
                it.freeze.onScan(scanKey(e))
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                sessions.get(pkg)?.let {
                    it.postReturn.onProductiveAction(now)
                    it.orbit.onCommit(now)
                    it.freeze.onSelect()
                }
                // The trail. Only from apps whose contents Thread is allowed to read.
                if (SensitiveApps.readContent(pkg)) {
                    textCapture.onTextChanged(pkg, e, now)
                    scheduleTextFlush()
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                if (OfficeApps.isSupported(pkg) && SensitiveApps.readContent(pkg)) {
                    sessions.get(pkg)?.let { session ->
                        session.rememberSelectedText(SelectionCapture.fromEvent(e))
                        rememberPlace(session, runCatching { e.source }.getOrNull(), now)
                    }
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // They acted. The step has been taken, so stop pointing at it.
                if (!isSystemSurface(pkg)) clearRing()
                sessions.get(pkg)?.let {
                    it.postReturn.onProductiveAction(now)
                    it.orbit.onCommit(now)
                    it.freeze.onSelect()
                }
            }
        }
    }

    /**
     * An event from an integrated app, routed to that app's session.
     *
     * The broadcast carries no package, so it is attributed to the app in front
     * of the user - which is the app that sent it, since a backgrounded app is
     * not the one the user is filling in. Dropped rather than misattributed if
     * there is no foreground session.
     */
    private fun applyDeclaredEvent(event: ThreadEvent) {
        val session = sessions.get(currentPackage ?: return) ?: return
        session.builder.apply(event)
        refreshDot()
    }

    /**
     * Read whatever field currently has input focus.
     *
     * Throttled hard. Content-changed fires dozens of times a second on a busy
     * screen, and walking to the focused node on each one would make Thread the
     * reason the user's phone feels slow - which for someone already struggling
     * would be its own kind of harm.
     */
    private fun captureFocusedField(pkg: String, now: Long) {
        if (now - lastFocusReadAt < focusReadMs) return
        lastFocusReadAt = now

        // A document app being typed into is a task worth holding even if Thread
        // never saw the user arrive - which happens whenever the service starts
        // while the app is already open, and would otherwise silently disable the
        // whole document beat.
        if (OfficeApps.exposesDocumentText(pkg)) {
            val session = sessions.get(pkg) ?: openSession(pkg, "$pkg/document", now)
            val place = PlaceCapture.fromRoot(hostWindowRoot(pkg), pkg, session.documentName, now)
            place?.let { session.builder.setPlace(it) }
        }

        val focused = runCatching {
            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull()
        if (focused == null) return

        textCapture.onFocusedNode(pkg, focused, now)
        scheduleTextFlush()
    }

    /**
     * Identifies the control a focus event came from, for counting how many
     * different things were looked at.
     *
     * Position is part of the key, and has to be. The first version used the
     * event's text and class name, which on the demo form gave both text fields
     * the same key - two blank Compose fields are indistinguishable by either -
     * so touching one after the other counted as looking at one thing. Freeze
     * scored 44 where it should have scored higher, and the undercount was
     * invisible because the number still moved.
     */
    private fun scanKey(e: AccessibilityEvent): String? {
        val node = runCatching { e.source }.getOrNull()
        node?.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { return it }

        val label = e.text.joinToString(" ").ifBlank { e.className?.toString().orEmpty() }
        val at = node?.let {
            val r = Rect()
            it.getBoundsInScreen(r)
            "@${r.left},${r.top}"
        }.orEmpty()

        return "$label$at".ifBlank { null }
    }

    /**
     * Commit typing once it stops, on a timer of its own.
     *
     * The obvious implementation - flush when the next accessibility event arrives
     * - never fires in the case that matters. Someone types a search, then stops
     * and looks at it, and the platform goes quiet: no event, no flush, and the
     * one thing they will want back is the one thing not recorded. So the flush
     * has to be driven by the clock, not by the next thing to happen.
     */
    private fun scheduleTextFlush() {
        main.removeCallbacksAndMessages(TEXT_TOKEN)
        main.postAtTime(
            { textCapture.flushIdle(System.currentTimeMillis()) },
            TEXT_TOKEN,
            SystemClock.uptimeMillis() + textFlushMs,
        )
    }

    /**
     * A field stopped changing. Record it as progress in that app's session.
     *
     * Note this is the same event type an integrated app sends through the SDK.
     * Tier 1 and Tier 2 differ in how reliably the label is known, not in kind, so
     * the card and every score treat them identically from here on.
     */
    private fun onTextCommitted(entry: TextCapture.Entry) {
        val session = sessions.get(entry.packageName) ?: return
        val now = System.currentTimeMillis()

        Log.d(TAG, "captured: ${entry.packageName} ${entry.label}='${entry.value}'")

        session.builder.apply(
            FieldCommit(
                ts = now,
                screenId = session.builder.state.currentScreenId,
                fieldId = entry.fieldId,
                label = entry.label,
                value = entry.value,
                isDecision = false,
            ),
        )
        refreshDot()
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

        val screenId = screenIdOf(pkg, e)
        val previous = currentPackage

        if (previous != null && previous != pkg) {
            onLeft(previous, pkg, now)
        }

        currentPackage = pkg

        val session = sessions.get(pkg) ?: openSession(pkg, screenId, now)
        sessions.touch(pkg, now)

        val awaySince = session.awayAt
        if (awaySince != null) {
            session.awayAt = null
            Log.d(TAG, "return to $pkg after ${(now - awaySince) / 1000}s onto $screenId")
            session.postReturn.begin(now)
            session.builder.apply(AppSwitchReturn(now, screenId, null))
            scheduleReturnEvaluation(pkg)
        } else {
            session.builder.apply(ScreenView(now, screenId))
            session.orbit.onScreen(screenId, now)
            scheduleOrbitEvaluation(pkg)
        }

        session.freeze.onScreen(screenId, now)
        scheduleFreezeEvaluation(pkg)

        refreshDot()
    }

    /**
     * The user has left an app for another one.
     *
     * Nothing about the app they left is discarded. That is the whole change: the
     * session stays, marked away, so that whatever they were doing is still there
     * when they come back - however many apps they visit in between.
     */
    private fun onLeft(fromPackage: String, toPackage: String, now: Long) {
        // Whatever they were part-way through typing, keep it. They did not stop
        // because they had finished.
        textCapture.flushAll()

        val session = sessions.get(fromPackage) ?: return
        if (session.awayAt != null) return

        session.awayAt = now
        Log.d(TAG, "away from $fromPackage -> $toPackage")
        session.builder.apply(
            AppSwitchAway(now, session.builder.state.currentScreenId, toPackage),
        )

        // Capture what they went to read, so it can be pinned when they get back.
        LookupDetector.capture(rootInActiveWindow)?.let {
            session.builder.apply(it.at(now, session.builder.state.currentScreenId, toPackage))
        }
    }

    /**
     * Start watching an app the user has just opened.
     *
     * Deliberately unconditional: every app gets a session, including ones whose
     * contents Thread will never read. Knowing that somebody stepped away to their
     * banking app is what makes the interruption visible, and it requires only the
     * package name.
     */
    private fun openSession(pkg: String, screenId: String, now: Long): Session {
        val session = Session(
            packageName = pkg,
            builder = TaskStateBuilder(
                taskId = UUID.randomUUID().toString(),
                intent = appLabel(pkg),
                startScreenId = screenId,
                startedAt = now,
            ),
            declared = false,
            lastSeenAt = now,
        )
        sessions.put(session)

        val dropped = sessions.evict()
        if (dropped.isNotEmpty()) Log.d(TAG, "evicted: $dropped")
        // Eviction drops the session, and everything read from that app goes with
        // it. Without this the captured text outlived the session it belonged to,
        // which is not what "dropped when the task ends" claims.
        dropped.forEach {
            textCapture.forget(it)
            PlaceCapture.forget(it)
        }

        Log.d(
            TAG,
            "session opened: $pkg screen=$screenId " +
                "readsContent=${SensitiveApps.readContent(pkg)} held=${sessions.all().size}",
        )
        return session
    }

    /**
     * Two looks, not one.
     *
     * The first catches someone who came back and stalled immediately. The second
     * catches the more common case: they start, realise they cannot remember where
     * they had got to, and stall a beat later. A single check would miss whichever
     * case it was not timed for.
     */
    private fun scheduleReturnEvaluation(pkg: String) {
        main.removeCallbacksAndMessages(RETURN_TOKEN)
        listOf(firstLookMs, secondLookMs).forEach { delay ->
            main.postAtTime(
                { evaluateAfterReturn(pkg, System.currentTimeMillis()) },
                RETURN_TOKEN,
                SystemClock.uptimeMillis() + delay,
            )
        }
    }

    /** Debounced: orbiting is a pattern over time, not a single navigation. */
    private fun scheduleOrbitEvaluation(pkg: String) {
        main.removeCallbacksAndMessages(ORBIT_TOKEN)
        main.postAtTime(
            { evaluateOrbit(pkg, System.currentTimeMillis()) },
            ORBIT_TOKEN,
            SystemClock.uptimeMillis() + 1_500L,
        )
    }

    /**
     * Freeze can only be judged by waiting, so this is a clock, not an event.
     *
     * It re-arms itself rather than firing a fixed number of times. Being stuck
     * deepens: the first minute on a screen looks identical whether someone is
     * reading carefully or cannot begin, and only the third minute tells them
     * apart. A fixed pair of looks would therefore check at exactly the times the
     * answer is least knowable and then stop watching just as it becomes clear.
     *
     * Cancelled on the next screen change, and gives up after
     * [freezeWatchWindowMs] - past that the user is doing something Thread has no
     * insight into, and continuing to poll their screen would be surveillance
     * with nothing to show for it.
     */
    private fun scheduleFreezeEvaluation(pkg: String) {
        main.removeCallbacksAndMessages(FREEZE_TOKEN)
        postFreezeLook(pkg, freezeFirstLookMs)
    }

    private fun postFreezeLook(pkg: String, delayMs: Long) {
        main.postAtTime(
            { evaluateFreeze(pkg, System.currentTimeMillis()) },
            FREEZE_TOKEN,
            SystemClock.uptimeMillis() + delayMs,
        )
    }

    /**
     * Standing still on one screen, weighing options, committing to none.
     *
     * Reads the tree twice over: once for the score's inputs, once for the plan.
     * Both come from the same flatten, so the score and the offer can never be
     * describing different screens - which they would be if the user moved
     * between two reads.
     */
    fun evaluateFreeze(pkg: String, now: Long) {
        val session = sessions.get(pkg) ?: return
        if (pkg != currentPackage) return

        val nodes = LiveComplexity.flatten(rootInActiveWindow)
        if (nodes.isEmpty()) return

        val facts = LiveFacts.facts(session.builder.state.currentScreenId, nodes)
        val freeze = DsFreeze.score(
            session.freeze.signals(
                now = now,
                optionCount = facts.optionCount,
                irreversiblePresent = facts.irreversibleActions > 0,
            ),
            weights,
        )

        val plan = Sequencer.plan(nodes)

        Log.d(
            TAG,
            "freeze[$pkg]: ${freeze.value.toInt()} factors=${freeze.factors} " +
                "plan=${plan?.let { "${it.position}/${it.total} next='${it.next?.label}'" } ?: "none"}",
        )

        if (plan == null) {
            Log.d(
                TAG,
                "no plan, candidates=" + nodes
                    .filter { it.isEditable || it.isCheckable }
                    .joinToString { "[ed=${it.isEditable} ck=${it.isCheckable} " +
                        "text='${it.text}' hint='${it.hintText}' cd='${it.contentDescription}']" },
            )
        }

        present(Triggers.evaluate(state = session.builder.state, freeze = freeze, plan = plan, w = weights), now)

        // Look again while they are still here and still have not chosen.
        if (pkg == currentPackage && session.freeze.stillWatching(now, freezeWatchWindowMs)) {
            postFreezeLook(pkg, freezeRecheckMs)
        }
    }

    private fun onScrolled(pkg: String, e: AccessibilityEvent, now: Long) {
        // Note: Jetpack Compose surfaces emit no TYPE_VIEW_SCROLLED at all, so this
        // never runs on a Compose UI. Verified on device; see docs/scoring.md.
        val session = sessions.get(pkg) ?: return
        val down = scrollingDown(e) ?: return
        if (!session.orbit.isReversal(down, now)) return

        Log.d(TAG, "scroll reversal in $pkg (now heading ${if (down) "down" else "up"})")
        session.builder.apply(ScrollReversal(now, session.builder.state.currentScreenId))
        session.postReturn.onScrollReversal()
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
    private fun smlFor(session: Session, screenId: String): Double {
        complexityCache[screenId]?.let { return it }
        session.liveSml[screenId]?.let { return it }

        val nodes = LiveComplexity.flatten(rootInActiveWindow)
        if (nodes.isEmpty()) {
            Log.d(TAG, "sml: $screenId - nothing readable, using neutral")
            return Sml.NEUTRAL
        }

        val facts = LiveFacts.facts(screenId, nodes)
        val value = Sml.score(facts, weights).value
        session.liveSml[screenId] = value

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
    fun evaluateAfterReturn(pkg: String, now: Long) {
        val session = sessions.get(pkg) ?: return
        val state = session.builder.state
        val sml = smlFor(session, state.currentScreenId)
        val cls = Cls.score(state, session.postReturn.signals(now), sml, weights)

        Log.d(TAG, "afterReturn[$pkg]: sml=$sml cls=${cls.value.toInt()} factors=${cls.factors}")

        val evaluation = Triggers.evaluate(
            state = state,
            cls = cls,
            orbit = DsOrbit.score(state, session.orbit.signals(now), weights),
            justReturned = true,
            w = weights,
        )

        present(evaluation, now)
    }

    /**
     * The other half. Someone who never left the app can still be lost inside it -
     * going back and forth to a screen because they cannot hold what is on it.
     */
    fun evaluateOrbit(pkg: String, now: Long) {
        val session = sessions.get(pkg) ?: return
        val state = session.builder.state
        val sml = smlFor(session, state.currentScreenId)

        val evaluation = Triggers.evaluate(
            state = state,
            cls = Cls.score(state, session.postReturn.signals(now), sml, weights),
            orbit = DsOrbit.score(state, session.orbit.signals(now), weights),
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
            else -> overlay.clearAutomaticCards()
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
        val pkg = currentPackage ?: return
        // The dot is always there now, so it must always answer. An app with no
        // session yet gets one here rather than a tap that does nothing.
        val session = sessions.get(pkg) ?: openSession(pkg, "$pkg/unknown", now)
        cardPackage = pkg
        val offer = OfferComposer.resumption(session.builder.state, triggeredBy = null)
        val breakdownContext = captureBreakdownContext(session)
        overlay.show(
            offer = arbiter.userRequested(offer, now),
            arbiter = arbiter,
            showTextInput = true,
            userRequested = true,
            onTextSubmitted = { text -> session.latestSubmittedText = text },
            breakdownContext = breakdownContext,
            initialToolState = session.toolExecutionState,
            onToolStateChanged = { state ->
                session.toolExecutionState = state
                // Dismissing the step takes the ring with it.
                if (state is ToolExecutionState.Idle) overlay.hideTarget()
            },
            onToolInvoked = { invocation, onStateChanged ->
                executeBreakdown(session, invocation, onStateChanged)
            },
            onAttachDocument = {
                overlay.hideCardsOnly()
                startActivity(
                    Intent(this, DocumentImportActivity::class.java)
                        .putExtra(
                            DocumentImportActivity.EXTRA_TARGET_PACKAGE,
                            session.packageName,
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onNext = { executeNextStep(session) },
        )
    }

    /** Guarded: events can arrive before the overlay exists. */
    private fun clearRing() {
        if (::overlay.isInitialized) overlay.hideTarget()
    }

    /**
     * The next single action on the screen behind the card.
     *
     * Runs entirely on device and returns immediately: no model, no network, no
     * quota, nothing to fail. That is the point of it being deterministic - the
     * user asked where to start, and an answer that depends on a round trip is
     * an answer that sometimes does not arrive.
     *
     * Returns the step rather than routing it through the tool state machine,
     * because the sequencer is synchronous: there is no request to track and no
     * loading state to show. Null means this screen has no order to work
     * through, which the card then says in as many words.
     */
    private fun executeNextStep(session: Session): Offer.NextStep? {
        val nodes = LiveComplexity.flattenForm(hostWindowRoot(session.packageName))
        val plan = Sequencer.plan(nodes)
        val offer = plan?.let { OfferComposer.nextStep(it, triggeredBy = null) }
        val target = offer?.target?.takeIf { it.isOnScreen() }

        Log.d(
            TAG,
            "next[${session.packageName}]: nodes=${nodes.size} " +
                "plan=${plan?.let { "${it.position}/${it.total} next='${it.next?.label}'" } ?: "none"} " +
                "target=${offer?.target} ringed=${target != null}",
        )

        if (target == null) overlay.hideTarget() else overlay.showTarget(target)
        return offer
    }

    /**
     * Whether a ring drawn here would land on the control it names.
     *
     * A target can now be below the fold, because the sequencer reads the whole
     * form rather than the viewport. It can also be larger than the screen - a
     * group of twenty-five options is one decision, and its bounds are the whole
     * list. In both cases the card still names the step; only the pointing stops.
     * A ring clamped to the screen edge would point, with the same confidence,
     * at whatever happens to be there.
     */
    private fun LiveFacts.Bounds.isOnScreen(): Boolean {
        val metrics = resources.displayMetrics
        return top >= 0 && left >= 0 &&
            bottom <= metrics.heightPixels && right <= metrics.widthPixels
    }

    /**
     * The node tree of the app the user is looking at, not of whatever holds focus.
     *
     * [rootInActiveWindow] follows input focus, and the card that asked the
     * question is itself focusable - so at the moment this runs, the active
     * window can be Thread's own overlay or the keyboard. Asking for the app's
     * window by name is the difference between a step that describes the user's
     * screen and one that confidently describes ours.
     */
    private fun hostWindowRoot(pkg: String): AccessibilityNodeInfo? {
        val named = runCatching {
            windows
                .asSequence()
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { window -> runCatching { window.root }.getOrNull() }
                .firstOrNull { it.packageName?.toString() == pkg }
        }.getOrNull()
        if (named != null) return named

        // Only if it is still the app in question. A root from the wrong package
        // is worse than none: it produces a plan that looks entirely plausible.
        val active = rootInActiveWindow
        return if (active?.packageName?.toString() == pkg) active else null
    }

    private fun executeBreakdown(
        session: Session,
        invocation: com.thread.app.tools.ToolInvocation,
        onStateChanged: (ToolExecutionState) -> Unit,
    ) {
        val effectiveInvocation = invocation.copy(
            input = invocation.input.ifBlank { session.builder.state.intent },
        )
        val requestId = nextToolRequestId.incrementAndGet()
        val loading = ToolExecutionState.Loading(requestId, effectiveInvocation)
        val intent = session.builder.state.intent
        session.toolExecutionState = loading
        onStateChanged(loading)

        toolScope.launch {
            val completedState = try {
                val breakdown = breakdownClient.generate(
                    task = effectiveInvocation.input,
                    intent = intent,
                    screenContext = effectiveInvocation.screenContext,
                )
                ToolExecutionState.Success(
                    requestId = requestId,
                    invocation = effectiveInvocation,
                    result = ToolResult.Breakdown(breakdown),
                )
            } catch (error: BreakdownClientException) {
                Log.w(TAG, "breakdown failed: ${error.message}", error)
                ToolExecutionState.Failure(
                    requestId = requestId,
                    invocation = effectiveInvocation,
                    message = error.message ?: "The AI service returned an invalid response.",
                )
            } catch (error: CancellationException) {
                throw error
            }

            main.post {
                if (sessions.get(session.packageName) !== session) return@post
                val active = session.toolExecutionState as? ToolExecutionState.Loading
                if (active?.requestId != requestId) return@post
                session.toolExecutionState = completedState
                onStateChanged(completedState)
            }
        }
    }

    /**
     * Keeps the line the user was writing, for apps that expose one.
     *
     * Cheap enough to run on every cursor move because it reads one node and does
     * arithmetic - no tree walk, no model call. The builder keeps only the latest,
     * so this replaces rather than accumulates.
     */
    /**
     * Keeps the line the user was writing, for apps that expose one.
     *
     * Driven from the focused node rather than from a text-changed event, for the
     * same reason [TextCapture.onFocusedNode] is: measured on device, this demo
     * editor emits no TYPE_VIEW_TEXT_CHANGED at all while typing. An implementation
     * hung off that event looks correct in review and captures nothing in use.
     *
     * Cheap enough to run on the throttled focus read because it reads one node and
     * does arithmetic - no tree walk, no model call.
     */
    private fun rememberPlace(session: Session, node: AccessibilityNodeInfo?, now: Long) {
        if (!OfficeApps.exposesDocumentText(session.packageName)) return
        val place = PlaceCapture.fromNode(node, session.documentName, now)
        if (place == null) return
        session.builder.setPlace(place)
    }

    private fun captureBreakdownContext(session: Session): BreakdownContext? {        val appName = OfficeApps.displayName(session.packageName) ?: return null
        if (!SensitiveApps.readContent(session.packageName)) return null
        return ScreenContextCollector.capture(
            root = hostWindowRoot(session.packageName),
            appName = appName,
            expectedPackage = session.packageName,
            selectedText = session.selectedText,
            documentName = session.documentName,
            documentText = session.documentText,
            readEditableDocumentText = OfficeApps.exposesDocumentText(session.packageName),
        )
    }

    fun latestSubmittedText(): String? =
        currentPackage?.let { sessions.get(it)?.latestSubmittedText }

    /**
     * The dot is always present, and the open card is kept in step with whatever
     * app the user is now looking at.
     *
     * It used to be shown only where there was something to pull, which meant it
     * vanished in exactly the apps Thread had not yet learned anything about. The
     * tap is the one interaction this design depends on being trusted, and a
     * button that is sometimes absent cannot be reached for without first
     * checking whether it is there - which is the remembering this is meant to
     * remove. An empty card is a smaller cost than an unreliable one.
     */
    private fun refreshDot() {
        if (!::overlay.isInitialized) return

        overlay.showDot { onDotTapped(System.currentTimeMillis()) }

        val session = currentPackage?.let { sessions.get(it) }
        if (session == null || !session.hasContext()) overlay.clearAutomaticCards()

        // An open card describes the app in front of the user, not the one they
        // were in when they opened it. Left alone it would keep displaying the
        // previous app's context, which is worse than showing nothing: it reads
        // exactly like a current answer.
        //
        // Only on an actual change of app. Rebuilding the card on every event
        // would discard whatever the user was part-way through typing into it.
        val pkg = currentPackage
        if (overlay.hasUserRequestedCard() && pkg != null && pkg != cardPackage) {
            onDotTapped(System.currentTimeMillis())
        }
    }

    /**
     * A declared task. Tier 2, and it always outranks an inferred session.
     *
     * Inferring task *boundaries* from the node tree is possible but unreliable,
     * and the cost of getting it wrong is asymmetric: a task that starts late
     * loses the very decisions worth restoring, and one that never ends holds
     * context it has no business holding. So an integrated app states them.
     *
     * Without integration Thread still runs - every app gets an inferred session
     * - it just observes behaviour instead of restoring declared content.
     */
    fun startTask(intent: String, packageName: String, screenId: String) {
        Log.d(TAG, "startTask: '$intent' pkg=$packageName screen=$screenId")

        sessions.put(
            Session(
                packageName = packageName,
                builder = TaskStateBuilder(
                    taskId = UUID.randomUUID().toString(),
                    intent = intent,
                    startScreenId = screenId,
                    startedAt = System.currentTimeMillis(),
                ),
                declared = true,
                lastSeenAt = System.currentTimeMillis(),
            ),
        )
        currentPackage = packageName

        // The dot appears only while a task is open, and it is the only thing
        // Thread ever shows unprompted.
        overlay.showDot { onDotTapped(System.currentTimeMillis()) }
    }

    /** "what you were doing in Excel" reads better than a package name. */
    private fun appLabel(packageName: String): String {
        // Android 11 package visibility means getApplicationInfo returns nothing for
        // apps Thread has not declared an interest in, and the fallback then puts a
        // raw package fragment on the card - "worddemo" rather than the app's name.
        // Broadening <queries> to fix that would buy a cosmetic gain with the right
        // to enumerate the device, so the known names are used first instead.
        val label = OfficeApps.displayName(packageName)
            ?: runCatching {
                val info = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrNull()
            ?: packageName.substringAfterLast('.')

        return "what you were doing in $label"
    }

    /**
     * Task over. Everything about *that app* is dropped, immediately.
     *
     * Scoped to the package on purpose. An integrated app finishing its task is
     * not a statement about the four other apps the user has open, and wiping
     * them would throw away context nobody asked to be rid of.
     */
    fun endTask(packageName: String? = null) {
        val target = packageName ?: currentPackage
        if (target == null) {
            Log.d(TAG, "endTask - no app in view, nothing to drop")
            return
        }
        Log.d(TAG, "endTask - dropping session for $target")
        main.removeCallbacksAndMessages(RETURN_TOKEN)
        main.removeCallbacksAndMessages(ORBIT_TOKEN)
        main.removeCallbacksAndMessages(FREEZE_TOKEN)
        textCapture.forget(target)
        PlaceCapture.forget(target)
        sessions.remove(target)
        refreshDot()
    }

    /** Opt-out, or shutdown. Everything, everywhere, gone. */
    private fun dropEverything() {
        main.removeCallbacksAndMessages(RETURN_TOKEN)
        main.removeCallbacksAndMessages(ORBIT_TOKEN)
        main.removeCallbacksAndMessages(FREEZE_TOKEN)
        sessions.clear()
        textCapture.clear()
        PlaceCapture.clear()
        currentPackage = null
        cardPackage = null
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
        dropEverything()
        toolScope.cancel()
        sdkReceiver?.let { runCatching { unregisterReceiver(it) } }
        sdkReceiver = null
        documentReceiver?.let { runCatching { unregisterReceiver(it) } }
        documentReceiver = null
        probeReceiver?.let { runCatching { unregisterReceiver(it) } }
        probeReceiver = null
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
            pkg == imePackage ||
            pkg in launcherPackages

    /**
     * The home screen is a corridor, not a room.
     *
     * Almost every app switch on Android passes through the launcher, so giving it
     * a session of its own produced lines like "return to the launcher after 35
     * seconds" - which describes nothing a user has ever lost. Leaving an app *via*
     * the launcher is still an interruption of that app, and is still recorded;
     * what stops is pretending the launcher was somewhere you were working.
     *
     * Resolving this with `resolveActivity` is a trap, and cost a regression before
     * it was caught in the log: on a device with no default launcher set, the
     * platform answers with `com.android.settings/.FallbackHome`, and Thread
     * silently stopped watching Settings altogether. Every home candidate is
     * collected instead, and Settings is never treated as one - a real launcher is
     * not going to be shipped inside it.
     */
    private fun resolveLauncherPackages() {
        launcherPackages = runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(home, 0)
                .map { it.activityInfo.packageName }
                .filterNot { it == "com.android.settings" }
                .toSet()
        }.getOrDefault(emptySet())
        Log.d(TAG, "ignoring launcher packages: $launcherPackages")
    }

    private fun refreshImePackage() {
        imePackage = runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
        }.getOrNull()
        Log.d(TAG, "ignoring IME package: $imePackage")
    }

    private companion object {
        const val TAG = "Thread"

        /**
         * Dump every event from every observed app to the log.
         *
         * Never true in a build anyone uses: it logs the text of everything on
         * screen, which is precisely the thing this project promises not to do.
         * Flipped on by hand, for a measurement, and flipped back.
         */
        const val PROBE_EVENTS = false

        /**
         * Registers an on-demand tree dump, triggered by broadcast.
         *
         * Same hazard as [PROBE_EVENTS] - it logs screen content - so it carries
         * the same rule: on by hand for a measurement, off before anything ships.
         */
        const val PROBE_DUMP = false
        const val PROBE_DUMP_ACTION = "com.thread.app.PROBE_DUMP"

        val RETURN_TOKEN = Any()
        val ORBIT_TOKEN = Any()
        val TEXT_TOKEN = Any()
        val FREEZE_TOKEN = Any()
    }
}
