package com.thread.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.thread.app.tools.BreakdownTool
import com.thread.app.tools.TaskBreakdown
import com.thread.app.tools.ThreadTool
import com.thread.app.tools.ToolExecutionState
import com.thread.app.tools.ToolInput
import com.thread.app.tools.ToolInvocation
import com.thread.app.tools.ToolRegistry
import com.thread.app.tools.ToolResult
import com.thread.engine.model.Offer

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
    showTextInput: Boolean = false,
    onTextSubmitted: (String) -> Unit = {},
    breakdownContext: BreakdownContext? = null,
    initialToolState: ToolExecutionState = ToolExecutionState.Idle,
    onToolStateChanged: (ToolExecutionState) -> Unit = {},
    onToolInvoked: (ToolInvocation, (ToolExecutionState) -> Unit) -> Unit = { _, _ -> },
) {
    when (offer) {
        is Offer.Resumption -> {
            var toolState by remember(initialToolState) { mutableStateOf(initialToolState) }

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
                ResumptionCard(offer, onAccept, onDismiss, onNever)
                when (val current = toolState) {
                    ToolExecutionState.Idle -> Unit
                    is ToolExecutionState.Loading -> ToolStatusPanel(
                        title = "Generating steps...",
                        message = current.invocation.input,
                    )
                    is ToolExecutionState.Success -> {
                        val result = current.result
                        if (result is ToolResult.Breakdown) {
                            TaskBreakdownPanel(result.value, ::updateBreakdown)
                        }
                    }
                    is ToolExecutionState.Failure -> ToolFailurePanel(
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
                    )
                }
            }
        }
        is Offer.Reassurance -> OneLineChip("${offer.consequence}. You can undo for ${offer.undoWindowSeconds / 60} minutes.", onDismiss)
        is Offer.ErrorExplanation -> OneLineChip(offer.message, onDismiss)
        is Offer.DefaultHint -> OneLineChip("${offer.suggestion}. ${offer.reversibility}.", onDismiss)
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
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .background(Surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Could not generate steps",
            color = OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(message, color = Muted, fontSize = 13.sp)
        Text(
            "Retry",
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
) {
    var text by remember { mutableStateOf("") }
    var highlightedIndex by remember { mutableStateOf(0) }
    var includeScreenContext by remember { mutableStateOf(false) }

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
                    screenContext = breakdownContext.takeIf { includeScreenContext },
                ),
            )
        }
        includeScreenContext = false
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

        if (selectedTool == BreakdownTool) {
            ScreenContextConsent(
                context = breakdownContext,
                checked = includeScreenContext,
                onCheckedChange = { includeScreenContext = it },
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
private fun ScreenContextConsent(
    context: BreakdownContext?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val available = !context?.visibleLabels.isNullOrEmpty()
    val preview = context?.visibleLabels
        ?.take(3)
        ?.joinToString(" • ")
        .orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .clickable(enabled = available) { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = available,
            onCheckedChange = onCheckedChange,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "Include visible screen context",
                color = if (available) OnSurface else Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (available) {
                    "Shares non-editable labels: $preview"
                } else {
                    "No safe readable labels are available"
                },
                color = Muted,
                fontSize = 11.sp,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun TaskBreakdownPanel(
    breakdown: TaskBreakdown,
    onChange: (TaskBreakdown) -> Unit,
) {
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
                .clickable { onChange(breakdown.toggleExpanded()) }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Task breakdown",
                    color = OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    breakdown.title,
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Text(
                "${breakdown.completedCount}/${breakdown.items.size}",
                color = Muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (breakdown.isExpanded) "▲" else "▼",
                color = Accent,
                fontSize = 12.sp,
            )
        }

        if (breakdown.isExpanded) {
            Column(
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                breakdown.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChange(breakdown.toggleItem(item.id)) }
                            .padding(vertical = 2.dp),
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
                    }
                }
            }
        }
    }
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
@Composable
fun ResumptionCard(
    offer: Offer.Resumption,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onNever: () -> Unit,
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
