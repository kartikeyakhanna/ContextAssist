package com.thread.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thread.app.tools.BreakdownContext
import com.thread.app.tools.TaskBreakdown
import com.thread.app.tools.ThreadTool
import com.thread.app.tools.ToolExecutionState
import com.thread.app.tools.ToolInput
import com.thread.app.tools.ToolInvocation
import com.thread.app.tools.ToolRegistry
import com.thread.app.tools.ToolResult
import com.thread.engine.model.Offer
import kotlinx.coroutines.delay

/**
 * The always-available way in.
 *
 * Small, still, and low contrast against nothing in particular. It carries no
 * state and no count, because a dot that changes appearance to signal "I have
 * something for you" is an interruption wearing a smaller coat.
 */
@Composable
fun ThreadDot(onTap: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(44.dp)
            .background(Surface.copy(alpha = 0.92f), CircleShape)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(10.dp)
                .background(Accent, CircleShape),
        )
    }
}

/**
 * Every surface the user ever sees. There are only three, deliberately.
 *
 * The rules below are not styling preferences - they are the accommodation:
 *
 *  - No badge, no pulse, no colour change to attract attention. A flashing
 *    indicator is an attack on precisely the person this is built for.
 *  - No red, no urgency colouring. Anxiety amplification is a failure mode.
 *  - Nothing auto-dismisses. Slower processing must not mean missing the content.
 *  - Three lines maximum, plain language. It is read by someone already depleted.
 *  - Every surface offers a way to stop showing it. Agency is the whole point.
 */

private val Surface = Color(0xFF1F2733)
private val OnSurface = Color(0xFFF2F4F7)
private val Muted = Color(0xFF9AA5B4)
private val Accent = Color(0xFF7FB3D5)

@Composable
fun ThreadSurface(
    offer: Offer,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onNever: () -> Unit,
    /**
     * Ask the sequencer for the next action on the screen behind the card.
     *
     * Returns its answer rather than taking a callback because the sequencer is
     * deterministic and on-device: there is no round trip to wait for, so there
     * is no loading state to model. Null means the screen has no order to work
     * through. Supplied only for a card the user opened; an offer that appeared
     * on its own keeps the buttons that let them stop it appearing.
     */
    onNext: (() -> Offer.NextStep?)? = null,
    showTextInput: Boolean = false,
    onTextSubmitted: (String) -> Unit = {},
    breakdownContext: BreakdownContext? = null,
    initialToolState: ToolExecutionState = ToolExecutionState.Idle,
    onToolStateChanged: (ToolExecutionState) -> Unit = {},
    onToolInvoked: (ToolInvocation, (ToolExecutionState) -> Unit) -> Unit = { _, _ -> },
    onAttachDocument: () -> Unit = {},
) {
    when (offer) {
        is Offer.Resumption -> {
            var toolState by remember(initialToolState) { mutableStateOf(initialToolState) }
            var nextStep by remember { mutableStateOf<Offer.NextStep?>(null) }
            var noSequence by remember { mutableStateOf(false) }

            fun updateToolState(updated: ToolExecutionState) {
                toolState = updated
                onToolStateChanged(updated)
            }

            fun invokeTool(invocation: ToolInvocation) {
                onToolInvoked(invocation, ::updateToolState)
            }

            fun updateBreakdown(updated: TaskBreakdown) {
                val success = toolState as? ToolExecutionState.Success ?: return
                updateToolState(
                    success.copy(result = ToolResult.Breakdown(updated)),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResumptionCard(
                    offer = offer,
                    onAccept = onAccept,
                    onDismiss = onDismiss,
                    onNever = onNever,
                    onNext = onNext?.let {
                        {
                            val found = it()
                            nextStep = found
                            noSequence = found == null
                        }
                    },
                )
                nextStep?.let { step ->
                    NextStepCard(step, onDismiss = { nextStep = null })
                }
                if (noSequence) {
                    // Said plainly rather than dressed up as a suggestion. Inventing
                    // a step for a screen that has no sequence is the one failure
                    // that would make this worse than silence.
                    ToolFailurePanel(
                        title = "Nothing to sequence here",
                        message = "This screen is not a form, so there is no order " +
                            "to work through. Nothing here has been changed.",
                        onRetry = { noSequence = false },
                        retryLabel = "Close",
                    )
                }
                when (val current = toolState) {
                    ToolExecutionState.Idle -> Unit
                    is ToolExecutionState.Loading -> ToolStatusPanel(
                        title = "Generating steps...",
                        message = current.invocation.input,
                    )
                    is ToolExecutionState.Success -> when (val result = current.result) {
                        is ToolResult.Breakdown -> TaskBreakdownPanel(
                            breakdown = result.value,
                            onChange = ::updateBreakdown,
                            onClear = { updateToolState(ToolExecutionState.Idle) },
                        )
                    }
                    is ToolExecutionState.Failure -> ToolFailurePanel(
                        title = "Could not generate steps",
                        message = current.message,
                        onRetry = { invokeTool(current.invocation) },
                    )
                }
                if (showTextInput) {
                    UserTextInputBar(
                        fallbackTask = offer.intent,
                        breakdownContext = breakdownContext,
                        onSubmit = onTextSubmitted,
                        onToolInvoked = ::invokeTool,
                        onAttachDocument = onAttachDocument,
                    )
                }
            }
        }
        is Offer.Reassurance -> OneLineChip("${offer.consequence}. You can undo for ${offer.undoWindowSeconds / 60} minutes.", onDismiss)
        is Offer.ErrorExplanation -> OneLineChip(offer.message, onDismiss)
        is Offer.DefaultHint -> OneLineChip("${offer.suggestion}. ${offer.reversibility}.", onDismiss)
        is Offer.NextStep -> NextStepCard(offer, onDismiss)
        is Offer.SequencingMode -> SequencingOffer(offer, onAccept, onDismiss)
        is Offer.Pin -> PinChip(offer.label, offer.value, onDismiss)
    }
}

@Composable
private fun ToolStatusPanel(
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            color = OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(message, color = Muted, fontSize = 13.sp, maxLines = 2)
    }
}

@Composable
private fun ToolFailurePanel(
    title: String,
    message: String,
    onRetry: () -> Unit,
    retryLabel: String = "Retry",
) {
    Column(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            color = OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(message, color = Muted, fontSize = 13.sp)
        Text(
            retryLabel,
            color = Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onRetry),
        )
    }
}

@Composable
private fun UserTextInputBar(
    fallbackTask: String,
    breakdownContext: BreakdownContext?,
    onSubmit: (String) -> Unit,
    onToolInvoked: (ToolInvocation) -> Unit,
    onAttachDocument: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var highlightedIndex by remember { mutableStateOf(0) }

    val suggestions = ToolInput.activeQuery(text)
        ?.let(ToolRegistry::search)
        .orEmpty()
    val selectedTool = ToolInput.selectedTool(text)
    val placeholder = selectedTool?.definition?.inputHint ?: "Type a message or @ for tools"

    fun selectTool(tool: ThreadTool) {
        text = ToolInput.select(tool)
        highlightedIndex = 0
    }

    fun performAction() {
        if (suggestions.isNotEmpty()) {
            selectTool(suggestions[highlightedIndex.coerceIn(suggestions.indices)])
            return
        }

        val submitted = text.trim()
        if (submitted.isEmpty()) return
        onSubmit(submitted)
        text = ""
        highlightedIndex = 0

        ToolInput.invocation(submitted)?.let { invocation ->
            val effectiveInvocation = if (invocation.input.isBlank()) {
                invocation.copy(input = fallbackTask)
            } else {
                invocation
            }
            onToolInvoked(
                effectiveInvocation.copy(
                    screenContext = breakdownContext,
                ),
            )
        }
    }

    Column(
        modifier = Modifier.widthIn(max = 340.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (suggestions.isNotEmpty()) {
            ToolMenu(
                tools = suggestions,
                highlightedIndex = highlightedIndex.coerceIn(suggestions.indices),
                onSelect = ::selectTool,
            )
        }

        if (breakdownContext?.appName == "Microsoft Word") {
            val documentName = breakdownContext.documentName
            Text(
                if (documentName == null) {
                    "Attach Word document"
                } else {
                    "Attached: $documentName · Replace"
                },
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(12.dp))
                    .clickable(onClick = onAttachDocument)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                highlightedIndex = 0
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { performAction() }),
            textStyle = TextStyle(color = OnSurface, fontSize = 15.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        suggestions.isNotEmpty() && event.key == Key.DirectionDown -> {
                            highlightedIndex = (highlightedIndex + 1) % suggestions.size
                            true
                        }
                        suggestions.isNotEmpty() && event.key == Key.DirectionUp -> {
                            highlightedIndex =
                                (highlightedIndex - 1 + suggestions.size) % suggestions.size
                            true
                        }
                        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                            performAction()
                            true
                        }
                        else -> false
                    }
                }
                .background(Surface, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(placeholder, color = Muted, fontSize = 15.sp)
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun TaskBreakdownPanel(
    breakdown: TaskBreakdown,
    onChange: (TaskBreakdown) -> Unit,
    onClear: () -> Unit,
) {
    val runningItem = breakdown.items.firstOrNull { it.isTimerRunning }
    LaunchedEffect(runningItem?.id, runningItem?.remainingSeconds) {
        if (runningItem != null && runningItem.remainingSeconds > 0) {
            delay(1_000)
            onChange(breakdown.tickTimer(runningItem.id))
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onChange(breakdown.toggleExpanded()) },
            ) {
                Text(
                    "Task breakdown",
                    color = OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${breakdown.title} · about ${breakdown.totalEstimateMinutes} min",
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Text(
                "New",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                "${breakdown.completedCount}/${breakdown.items.size}",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onChange(breakdown.toggleExpanded()) },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (breakdown.isExpanded) "▲" else "▼",
                color = Accent,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onChange(breakdown.toggleExpanded()) },
            )
        }

        if (breakdown.isExpanded) {
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                breakdown.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = item.isCompleted,
                            onCheckedChange = { onChange(breakdown.toggleItem(item.id)) },
                        )
                        Text(
                            item.text,
                            color = if (item.isCompleted) Muted else OnSurface,
                            fontSize = 14.sp,
                            textDecoration =
                                if (item.isCompleted) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                formatTimer(item.remainingSeconds),
                                color = if (item.isTimerRunning) Accent else Muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                when {
                                    item.isCompleted -> "Done"
                                    item.isTimerRunning -> "Pause"
                                    item.remainingSeconds == 0 -> "Restart"
                                    else -> "Start"
                                },
                                color = if (item.isCompleted) Muted else Accent,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable(enabled = !item.isCompleted) {
                                    onChange(breakdown.toggleTimer(item.id))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun ToolMenu(
    tools: List<ThreadTool>,
    highlightedIndex: Int,
    onSelect: (ThreadTool) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tools.forEachIndexed { index, tool ->
            val definition = tool.definition
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index == highlightedIndex) Color(0xFF2A4052) else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(tool) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    definition.command,
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(88.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        definition.displayName,
                        color = OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(definition.description, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * The hero surface. Restores what the task was, what is done, what was decided,
 * and what comes next.
 *
 * The "You chose" line is the one that earns its place: restoring *what* they did
 * is useful, but restoring *why* is what stops them re-deliberating a decision
 * they already made before the interruption.
 */
/**
 * The two shapes this card takes.
 *
 * An offer that appeared on its own must carry its own off-switch - "Not now"
 * teaches the arbiter it was wrong, "Never" stops that kind outright, and an
 * unbidden overlay without either is the pattern this project exists to avoid.
 * A card the user opened by tapping the dot needs neither: they asked, and
 * closing it is already the answer. That space goes to "Next" instead.
 */
@Composable
fun ResumptionCard(
    offer: Offer.Resumption,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onNever: () -> Unit,
    onNext: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Where you were", color = Muted, fontSize = 13.sp)
        Text(offer.intent, color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

        offer.place?.let { Line("Writing", it) }
        offer.done?.let { Line("Done", it) }
        offer.decided?.let { Line("You chose", it) }
        offer.next?.let { Line("Next", it) }
        offer.awayFor?.let { Text(it, color = Muted, fontSize = 13.sp) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Got it",
                color = Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onAccept),
            )
            if (onNext != null) {
                Text(
                    "Next",
                    color = Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onNext),
                )
            } else {
                Text(
                    "Not now",
                    color = Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Never",
                    color = Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(onClick = onNever),
                )
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$label ",
            color = Muted,
            fontSize = 14.sp,
            modifier = Modifier.width(78.dp),
        )
        Text(value, color = OnSurface, fontSize = 14.sp)
    }
}

/**
 * The working-memory prosthetic, and on mobile the most valuable surface here.
 *
 * On a phone you cannot see two things at once - so a value read in another app
 * is simply gone by the time you are back, and you go and fetch it again. The
 * loop is not confusion; it is the working-memory failure itself. So Thread holds
 * the value rather than explaining the screen.
 */
@Composable
fun PinChip(label: String, value: String, onUnpin: () -> Unit) {
    Row(
        modifier = Modifier
            .background(Surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onUnpin)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, color = OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OneLineChip(text: String, onDismiss: () -> Unit) {
    Text(
        text = text,
        color = OnSurface,
        fontSize = 14.sp,
        modifier = Modifier
            .widthIn(max = 320.dp)
            .background(Surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * One action, and where you are in the list. Never the whole list.
 *
 * Nothing here is a control of the host app - it is a sentence about one. The
 * user taps the real thing on the real screen, in the place the app put it, and
 * every other option is still there. This card can be ignored completely and the
 * task still works exactly as it did, which is the property that makes it safe to
 * be wrong.
 */
@Composable
private fun NextStepCard(offer: Offer.NextStep, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(offer.instruction, color = OnSurface, fontSize = 15.sp)
            Text(
                "${offer.position} of ${offer.total}",
                color = Muted,
                fontSize = 12.sp,
            )
        }
        Text(
            "Dismiss",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onDismiss),
        )
    }
}

/**
 * The ring drawn over the real control on the screen beneath.
 *
 * Deliberately an outline and not a fill or a spotlight: the user still has to
 * read the control's own label to act on it, so covering it or dimming the rest
 * of the screen would remove the very thing they are being sent to.
 */
@Composable
fun TargetRing() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(2.dp, Accent, RoundedCornerShape(8.dp)),
    )
}

/**
 * A translucent band over the line the user was writing.
 *
 * Deliberately tinted rather than outlined. The text underneath has to stay
 * legible - the point is to let someone find their place and carry on reading,
 * not to obscure the words they came back for.
 */
@Composable
fun LineHighlight() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Accent.copy(alpha = 0.22f), RoundedCornerShape(4.dp)),
    )
}

/** Sequencing is a lens over the form, never a deletion. Every field stays reachable. */
@Composable
private fun SequencingOffer(
    offer: Offer.SequencingMode,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Want to go one section at a time? " +
                "${offer.sectionCount} sections, about ${offer.estimateMinutes} minutes.",
            color = OnSurface,
            fontSize = 14.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                "Yes",
                color = Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onAccept),
            )
            Text(
                "No thanks",
                color = Muted,
                fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
    }
}
