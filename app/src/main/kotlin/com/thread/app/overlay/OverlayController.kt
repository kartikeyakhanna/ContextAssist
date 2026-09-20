package com.thread.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.thread.app.tools.BreakdownContext
import com.thread.app.tools.ToolExecutionState
import com.thread.app.tools.ToolInvocation
import com.thread.engine.Arbiter
import com.thread.engine.model.Offer
import com.thread.engine.model.OfferKind
import com.thread.engine.model.OfferOutcome
import com.thread.engine.scores.LiveFacts

/**
 * Draws Thread's surfaces on top of whatever app the user is in.
 *
 * Two hard constraints, both of which are the product rather than limitations of it:
 *
 *  - The window is NOT_FOCUSABLE. Thread never takes input from the app beneath,
 *    never blocks an action, and never gates anything. It is an aid, not a gate.
 *  - Nothing ever auto-dismisses. Timed content is a documented barrier for people
 *    with slower processing, and this audience is exactly who would lose it. A card
 *    waits as long as it takes.
 */
class OverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var cardView: View? = null
    private var pinView: View? = null
    private var dotView: View? = null
    private var targetView: View? = null

    /**
     * Whether the card currently on screen was asked for, rather than offered.
     *
     * The detection loop re-runs on every accessibility event, and a card that is
     * focusable generates those events simply by existing. Without this, the act
     * of opening the card is itself what closes it. More importantly: a user who
     * taps the dot has asked a question, and no score is entitled to withdraw the
     * answer while they are still reading it.
     */
    private var cardIsUserRequested = false

    /**
     * The only thing Thread shows without being asked.
     *
     * A small, static dot - no badge, no count, no pulse. It is deliberately not
     * an alert: the person this is built for is already managing more incoming
     * signal than they can comfortably process, and a thing that flashes for
     * attention would be one more of them.
     *
     * Its real job is to make asking cheap. Tapping it requires no recall and no
     * phrasing, which matters because the moment you need help remembering is the
     * worst moment to be asked to compose a question.
     */
    fun showDot(onTap: () -> Unit) {
        if (dotView != null) return
        dotView = composeOverlay(Gravity.BOTTOM or Gravity.START) {
            ThreadDot(onTap = onTap)
        }
    }

    fun show(
        offer: Offer,
        arbiter: Arbiter,
        showTextInput: Boolean = false,
        userRequested: Boolean = false,
        onNext: (() -> Offer.NextStep?)? = null,
        onTextSubmitted: (String) -> Unit = {},
        breakdownContext: BreakdownContext? = null,
        initialToolState: ToolExecutionState = ToolExecutionState.Idle,
        onToolStateChanged: (ToolExecutionState) -> Unit = {},
        onToolInvoked: (ToolInvocation, (ToolExecutionState) -> Unit) -> Unit = { _, _ -> },
        onAttachDocument: () -> Unit = {},
    ) {
        when (offer) {
            is Offer.Pin -> showPin(offer, arbiter)

            else -> {
                // An offer the user did not ask for never displaces an answer they did.
                if (cardIsUserRequested && !userRequested) return
                showCard(
                    offer = offer,
                    arbiter = arbiter,
                    showTextInput = showTextInput,
                    userRequested = userRequested,
                    onNext = onNext,
                    onTextSubmitted = onTextSubmitted,
                    breakdownContext = breakdownContext,
                    initialToolState = initialToolState,
                    onToolStateChanged = onToolStateChanged,
                    onToolInvoked = onToolInvoked,
                    onAttachDocument = onAttachDocument,
                )
            }

        }
    }

    private fun showCard(
        offer: Offer,
        arbiter: Arbiter,
        showTextInput: Boolean,
        userRequested: Boolean,
        onNext: (() -> Offer.NextStep?)?,
        onTextSubmitted: (String) -> Unit,
        breakdownContext: BreakdownContext?,
        initialToolState: ToolExecutionState,
        onToolStateChanged: (ToolExecutionState) -> Unit,
        onToolInvoked: (ToolInvocation, (ToolExecutionState) -> Unit) -> Unit,
        onAttachDocument: () -> Unit,
    ) {
        hideCard()
        cardIsUserRequested = userRequested
        cardView = composeOverlay(
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            focusable = showTextInput,
            // Tapping the screen behind the card puts it away, and the same tap
            // still reaches the control underneath. That is what makes the ring
            // usable: the user taps the field it points at and starts typing,
            // rather than closing us first and then finding the field again.
            //
            // Recorded as IGNORED, not accepted. Someone reaching past the card
            // to get on with their work has not told us we were right, and
            // counting it as agreement would flatter the one number - accepted
            // over shown - that says whether this helps anyone.
            onOutsideTouch = {
                if (!userRequested) {
                    arbiter.record(offer.kind, OfferOutcome.IGNORED, System.currentTimeMillis())
                }
                hideCard()
            },
        ) {
            ThreadSurface(
                offer = offer,
                onAccept = {
                    arbiter.record(offer.kind, OfferOutcome.ACCEPTED, System.currentTimeMillis())
                    hideCard()
                },
                onDismiss = {
                    arbiter.record(offer.kind, OfferOutcome.DISMISSED, System.currentTimeMillis())
                    hideCard()
                },
                onNever = {
                    arbiter.record(offer.kind, OfferOutcome.SUPPRESSED, System.currentTimeMillis())
                    hideCard()
                },
                showTextInput = showTextInput,
                onTextSubmitted = onTextSubmitted,
                breakdownContext = breakdownContext,
                onNext = onNext,
                initialToolState = initialToolState,
                onToolStateChanged = onToolStateChanged,
                onToolInvoked = onToolInvoked,
                onAttachDocument = onAttachDocument,
            )
        }
    }

    /**
     * The pin sits apart from the card lifecycle. It is not a notification to be
     * cleared - it is a value being held on the user's behalf, so it stays put
     * until the task ends or they swipe it away.
     */
    private fun showPin(offer: Offer.Pin, arbiter: Arbiter) {
        hidePin()
        pinView = composeOverlay(Gravity.BOTTOM or Gravity.END, focusable = false) {
            PinChip(
                label = offer.label,
                value = offer.value,
                onUnpin = {
                    arbiter.record(OfferKind.PIN, OfferOutcome.SUPPRESSED, System.currentTimeMillis())
                    hidePin()
                },
            )
        }
    }

    /** The passive band: a quiet marker, never a card. No badge, no animation. */
    fun showPassiveMarker() {
        clearAutomaticCards()
    }

    /**
     * Clears offers the detection loop put up, and only those.
     *
     * A card the user asked for survives this. The loop re-evaluates on every
     * accessibility event, so treating "nothing to offer" as "take the answer
     * away" would close the card roughly as fast as tapping the dot opened it.
     */
    fun clearAutomaticCards() {
        if (cardIsUserRequested) return
        hideCard()
    }

    /**
     * Clears offers but leaves the dot alone.
     *
     * The dot is not an offer - it is the way in. Removing it whenever the scores
     * fall quiet would mean the button for "where was I?" disappears precisely
     * when detection has decided nothing is wrong, which is exactly the case
     * where the user needs to be able to ask.
     */
    fun hideCardsOnly() {
        hideCard()
    }

    /** Whether the card on screen was asked for, rather than offered. */
    fun hasUserRequestedCard(): Boolean = cardIsUserRequested

    fun hide() {
        hideCard()
        hidePin()
        hideDot()
        hideTarget()
    }

    private fun hideDot() {
        dotView?.let { runCatching { windowManager.removeView(it) } }
        dotView = null
    }

    private fun hideCard() {
        cardView?.let { runCatching { windowManager.removeView(it) } }
        cardView = null
        cardIsUserRequested = false
        // The ring is the card's pointing finger. It must never outlive the
        // sentence that explains what it is pointing at.
        hideTarget()
    }

    private fun hidePin() {
        pinView?.let { runCatching { windowManager.removeView(it) } }
        pinView = null
    }

    /**
     * A ring around the control the next step refers to.
     *
     * This is the part that makes it navigation rather than instruction: the
     * user looks where the ring is instead of reading a sentence and then
     * searching the screen for what it named.
     *
     * It draws over the app and changes nothing in it. No view is moved, hidden
     * or disabled, and every other control stays exactly where the user left it.
     */
    fun showTarget(bounds: LiveFacts.Bounds?) {
        hideTarget()
        if (bounds == null) return

        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        if (width <= 0 || height <= 0) return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { TargetRing() }
        }

        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // NOT_TOUCHABLE is load-bearing. Without it the ring would swallow
            // taps on the one control it exists to send the user to.
            //
            // LAYOUT_IN_SCREEN is what makes the coordinates mean the same thing
            // as the ones the node reported. getBoundsInScreen measures from the
            // true top of the display; without this flag the overlay is laid out
            // below the status bar, and the ring lands a status bar's height low
            // - which on this screen is one form field, the most plausible and
            // most misleading error it could make.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
        }

        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                owner.onStart()
                targetView = view
            }
    }

    /**
     * Cleared whenever the screen may have moved under it.
     *
     * A ring at stale coordinates is worse than no ring: it points, with the same
     * confidence, at whatever has scrolled into that position instead.
     */
    fun hideTarget() {
        targetView?.let { runCatching { windowManager.removeView(it) } }
        targetView = null
    }

    @SuppressLint("InflateParams")
    private fun composeOverlay(
        gravity: Int,
        focusable: Boolean = false,
        onOutsideTouch: (() -> Unit)? = null,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ): View {
        val owner = OverlayLifecycleOwner().apply { onCreate() }

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { content() }
        }

        if (onOutsideTouch != null) {
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    onOutsideTouch()
                    true
                } else {
                    false
                }
            }
        }

        var flags = if (focusable) {
            // NO_LIMITS is deliberately absent here. A focusable window that
            // ignores insets tells the keyboard there is no navigation bar to
            // leave room for, so the keyboard draws over Back and Home - and
            // the user cannot leave the screen without first dealing with us.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (onOutsideTouch != null) {
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            // Cards clear the dot rather than landing on top of it. The dot is
            // the way in and must stay tappable even while a card is showing.
            y = if (gravity and Gravity.START == Gravity.START) 96 else 260
            x = 24
        }

        windowManager.addView(view, params)
        owner.onStart()
        return view
    }
}

/** Minimal owner so Compose can run outside an Activity. */
private class OverlayLifecycleOwner :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(Bundle())
        registry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        registry.currentState = Lifecycle.State.RESUMED
    }
}
