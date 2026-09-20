package com.thread.worddemo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class WordDemoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = WordBlue,
                    secondary = WordBlue,
                ),
            ) {
                WordLikeEditor()
            }
        }
    }
}

private val WordBlue = Color(0xFF2B579A)
private val RibbonBackground = Color(0xFFF4F6F9)
private val PageBackground = Color(0xFFE7E9EC)
private val PageBorder = Color(0xFFD0D4DA)
private val Ink = Color(0xFF202124)
private val Muted = Color(0xFF68707A)
private val ActiveControl = Color(0xFFDCE8F7)

@Composable
private fun WordLikeEditor() {
    var document by remember {
        mutableStateOf(
            TextFieldValue(
                text = SAMPLE_DOCUMENT,
                selection = TextRange(0),
            ),
        )
    }
    var formatRanges by remember { mutableStateOf(emptyList<FormatRange>()) }
    val undoHistory = remember { mutableStateListOf<DocumentSnapshot>() }
    val redoHistory = remember { mutableStateListOf<DocumentSnapshot>() }
    var saveStatus by remember { mutableStateOf("Saved") }
    var activeTab by remember { mutableStateOf("Home") }
    var showSearch by remember { mutableStateOf(false) }
    var commentedText by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    var documentLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var editorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var draggingSelectionHandle by remember { mutableStateOf(false) }
    var pendingTypedText by remember { mutableStateOf("") }
    val documentFocusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    fun replaceDocument(updated: TextFieldValue) {
        if (draggingSelectionHandle && updated.text == document.text) return
        if (updated.text != document.text) {
            newlyInsertedText(
                before = document.text,
                after = updated.text,
                replacedRange = document.selection,
            )?.let { inserted ->
                pendingTypedText = (pendingTypedText + inserted).takeLast(MAX_TYPED_ENTRY_LENGTH)
            }
            val replacedRange = document.selection
            val insertedLength =
                updated.text.length - (document.text.length - replacedRange.length)
            undoHistory += DocumentSnapshot(document.text, formatRanges)
            redoHistory.clear()
            formatRanges = remapFormatRanges(
                ranges = formatRanges,
                replacedRange = replacedRange,
                insertedLength = insertedLength.coerceAtLeast(0),
                textLength = updated.text.length,
            )
            saveStatus = "Saving"
        }
        document = updated
    }

    fun selectedText(): String? {
        val range = document.selection
        if (range.collapsed) return null
        return document.text.substring(range.min, range.max)
    }

    fun copySelection(remove: Boolean) {
        val selected = selectedText() ?: return
        clipboard.setText(AnnotatedString(selected))
        if (remove) {
            val range = document.selection
            replaceDocument(
                TextFieldValue(
                    text = document.text.removeRange(range.min, range.max),
                    selection = TextRange(range.min),
                ),
            )
        }
    }

    fun paste() {
        val pasted = clipboard.getText()?.text ?: return
        val range = document.selection
        replaceDocument(
            TextFieldValue(
                text = document.text.replaceRange(range.min, range.max, pasted),
                selection = TextRange(range.min + pasted.length),
            ),
        )
    }

    fun toggleSelectionFormat(format: DemoTextFormat) {
        val selection = document.selection
        if (selection.collapsed) return
        undoHistory += DocumentSnapshot(document.text, formatRanges)
        redoHistory.clear()
        formatRanges = toggleFormat(formatRanges, selection, format)
        saveStatus = "Saving"
    }

    fun commentOnSelection() {
        commentedText = selectedText()
    }

    LaunchedEffect(document.text, saveStatus) {
        if (saveStatus == "Saving") {
            delay(900)
            saveStatus = "Saved"
        }
    }

    LaunchedEffect(pendingTypedText) {
        val pending = pendingTypedText
        if (pending.isEmpty()) return@LaunchedEffect
        delay(TYPING_COMMIT_DELAY_MS)
        pending
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let(context::reportTypedDocumentEntry)
        pendingTypedText = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .systemBarsPadding(),
    ) {
        TopAppBar(
            saveStatus = saveStatus,
            canUndo = undoHistory.isNotEmpty(),
            canRedo = redoHistory.isNotEmpty(),
            onUndo = {
                undoHistory.removeLastOrNull()?.let { previous ->
                    redoHistory += DocumentSnapshot(document.text, formatRanges)
                    formatRanges = previous.formatRanges
                    document = TextFieldValue(previous.text, TextRange(previous.text.length))
                    saveStatus = "Saving"
                }
            },
            onRedo = {
                redoHistory.removeLastOrNull()?.let { next ->
                    undoHistory += DocumentSnapshot(document.text, formatRanges)
                    formatRanges = next.formatRanges
                    document = TextFieldValue(next.text, TextRange(next.text.length))
                    saveStatus = "Saving"
                }
            },
            onSearch = { showSearch = !showSearch },
        )

        if (showSearch) {
            SearchBar(
                value = searchText,
                onValueChange = { searchText = it },
                matchCount = if (searchText.isBlank()) {
                    0
                } else {
                    Regex(Regex.escape(searchText), RegexOption.IGNORE_CASE)
                        .findAll(document.text)
                        .count()
                },
            )
        }

        val selection = document.selection
        val hasSelection = !selection.collapsed
        Ribbon(
            selectionAvailable = hasSelection,
            bold = selectionHasFormat(formatRanges, selection, DemoTextFormat.Bold),
            italic = selectionHasFormat(formatRanges, selection, DemoTextFormat.Italic),
            underline = selectionHasFormat(formatRanges, selection, DemoTextFormat.Underline),
            highlight = selectionHasFormat(formatRanges, selection, DemoTextFormat.Highlight),
            fontColor = selectionHasFormat(formatRanges, selection, DemoTextFormat.FontColor),
            onCut = { copySelection(remove = true) },
            onCopy = { copySelection(remove = false) },
            onPaste = ::paste,
            onSelectAll = {
                document = document.copy(selection = TextRange(0, document.text.length))
            },
            onBold = { toggleSelectionFormat(DemoTextFormat.Bold) },
            onItalic = { toggleSelectionFormat(DemoTextFormat.Italic) },
            onUnderline = { toggleSelectionFormat(DemoTextFormat.Underline) },
            onHighlight = { toggleSelectionFormat(DemoTextFormat.Highlight) },
            onFontColor = { toggleSelectionFormat(DemoTextFormat.FontColor) },
            onComment = ::commentOnSelection,
        )

        SelectionStatusBar(
            selectedText = selectedText(),
            onCut = { copySelection(remove = true) },
            onCopy = { copySelection(remove = false) },
            onComment = ::commentOnSelection,
            onClear = {
                document = document.copy(selection = TextRange(document.selection.max))
            },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .background(Color.White, RoundedCornerShape(3.dp))
                .border(1.dp, PageBorder, RoundedCornerShape(3.dp))
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "The Benefits of Urban Green Spaces",
                    color = Ink,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { editorCoordinates = it },
                ) {
                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = Color.Transparent,
                            backgroundColor = WordBlue.copy(alpha = 0.28f),
                        ),
                    ) {
                        BasicTextField(
                            value = document,
                            onValueChange = ::replaceDocument,
                            onTextLayout = { documentLayout = it },
                            cursorBrush = SolidColor(WordBlue),
                            textStyle = TextStyle(
                                color = Ink,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                            ),
                            visualTransformation = SelectionFormattingTransformation(formatRanges),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(documentFocusRequester)
                                .pointerInput(document.text, documentLayout) {
                                    observeDoubleTaps { position ->
                                        val layout = documentLayout ?: return@observeDoubleTaps
                                        val offset = layout.getOffsetForPosition(position)
                                        document = document.copy(
                                            selection = layout.getWordBoundary(offset),
                                        )
                                        focusManager.clearFocus(force = true)
                                    }
                                }
                                .semantics {
                                    contentDescription =
                                        "Document body. Double tap a word to select it, then " +
                                            "drag the start and end handles to change the selection."
                                },
                        )
                    }

                    val selection = document.selection
                    val layout = documentLayout
                    if (!selection.collapsed && layout != null) {
                        DemoSelectionHandle(
                            label = "Start selection handle",
                            position = layout.selectionHandlePosition(selection.min, start = true),
                            editorCoordinates = editorCoordinates,
                            onDragStateChanged = { draggingSelectionHandle = it },
                            onPositionChanged = { position ->
                                val offset = layout.getOffsetForPosition(position.coerceIn(layout))
                                    .coerceIn(0, selection.max - 1)
                                document = document.copy(
                                    selection = TextRange(offset, selection.max),
                                )
                            },
                        )
                        DemoSelectionHandle(
                            label = "End selection handle",
                            position = layout.selectionHandlePosition(selection.max, start = false),
                            editorCoordinates = editorCoordinates,
                            onDragStateChanged = { draggingSelectionHandle = it },
                            onPositionChanged = { position ->
                                val offset = layout.getOffsetForPosition(position.coerceIn(layout))
                                    .coerceIn(selection.min + 1, document.text.length)
                                document = document.copy(
                                    selection = TextRange(selection.min, offset),
                                )
                            },
                        )
                    }
                }
                commentedText?.let { comment ->
                    Text(
                        "Comment on \"${comment.take(80)}\": " +
                            "Add one measurable example to support this text.",
                        color = WordBlue,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEAF2FC), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    )
                }
            }
        }

        BottomBar(
            activeTab = activeTab,
            wordCount = document.text.split(Regex("\\s+")).count { it.isNotBlank() },
            onSelect = { activeTab = it },
        )
    }
}

@Composable
private fun DemoSelectionHandle(
    label: String,
    position: Offset,
    editorCoordinates: LayoutCoordinates?,
    onDragStateChanged: (Boolean) -> Unit,
    onPositionChanged: (Offset) -> Unit,
) {
    val currentPosition by rememberUpdatedState(position)
    val currentEditorCoordinates by rememberUpdatedState(editorCoordinates)
    val currentOnDragStateChanged by rememberUpdatedState(onDragStateChanged)
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)
    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val handleSize = 24.dp

    Box(
        modifier = Modifier
            .zIndex(1f)
            .offset {
                IntOffset(
                    x = (currentPosition.x - handleSize.toPx() / 2).roundToInt(),
                    y = currentPosition.y.roundToInt(),
                )
            }
            .size(handleSize)
            .background(WordBlue, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .onGloballyPositioned { handleCoordinates = it }
            .semantics {
                role = Role.Button
                contentDescription = "$label. Drag to adjust selected text."
            }
            .pointerInput(label) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    down.consume()
                    val grabOffset =
                        down.position - Offset(handleSize.toPx() / 2f, 0f)
                    currentOnDragStateChanged(true)
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val handle = handleCoordinates
                            val editor = currentEditorCoordinates
                            if (handle != null && editor != null) {
                                currentOnPositionChanged(
                                    editor.localPositionOf(handle, change.position) - grabOffset,
                                )
                            }
                            change.consume()
                            if (!change.pressed) break
                        }
                    } finally {
                        currentOnDragStateChanged(false)
                    }
                }
            },
    )
}

private fun TextLayoutResult.selectionHandlePosition(
    offset: Int,
    start: Boolean,
): Offset {
    val safeOffset = offset.coerceIn(0, layoutInput.text.text.length)
    val cursor = getCursorRect(safeOffset)
    return Offset(
        x = if (start) cursor.left else cursor.right,
        y = cursor.bottom,
    )
}

private fun Offset.coerceIn(layout: TextLayoutResult): Offset = Offset(
    x = x.coerceIn(0f, layout.size.width.toFloat()),
    y = y.coerceIn(0f, layout.size.height.toFloat()),
)

internal enum class DemoTextFormat {
    Bold,
    Italic,
    Underline,
    Highlight,
    FontColor,
}

internal data class FormatRange(
    val start: Int,
    val end: Int,
    val format: DemoTextFormat,
)

private data class DocumentSnapshot(
    val text: String,
    val formatRanges: List<FormatRange>,
)

private class SelectionFormattingTransformation(
    private val ranges: List<FormatRange>,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = buildAnnotatedString {
            append(text)
            ranges.forEach { range ->
                val start = range.start.coerceIn(0, text.length)
                val end = range.end.coerceIn(start, text.length)
                if (start == end) return@forEach
                addStyle(range.format.spanStyle(), start, end)
            }
        }
        return TransformedText(formatted, OffsetMapping.Identity)
    }
}

private fun DemoTextFormat.spanStyle(): SpanStyle = when (this) {
    DemoTextFormat.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    DemoTextFormat.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    DemoTextFormat.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
    DemoTextFormat.Highlight -> SpanStyle(background = Color(0xFFFFF59D))
    DemoTextFormat.FontColor -> SpanStyle(color = WordBlue)
}

internal fun selectionHasFormat(
    ranges: List<FormatRange>,
    selection: TextRange,
    format: DemoTextFormat,
): Boolean {
    if (selection.collapsed) return false
    var coveredThrough = selection.min
    ranges
        .asSequence()
        .filter { it.format == format && it.end > selection.min && it.start < selection.max }
        .sortedBy(FormatRange::start)
        .forEach { range ->
            if (range.start > coveredThrough) return false
            coveredThrough = maxOf(coveredThrough, range.end)
            if (coveredThrough >= selection.max) return true
        }
    return false
}

internal fun toggleFormat(
    ranges: List<FormatRange>,
    selection: TextRange,
    format: DemoTextFormat,
): List<FormatRange> {
    if (selection.collapsed) return ranges
    val updated = if (selectionHasFormat(ranges, selection, format)) {
        ranges.flatMap { range ->
            if (
                range.format != format ||
                range.end <= selection.min ||
                range.start >= selection.max
            ) {
                listOf(range)
            } else {
                buildList {
                    if (range.start < selection.min) {
                        add(range.copy(end = selection.min))
                    }
                    if (range.end > selection.max) {
                        add(range.copy(start = selection.max))
                    }
                }
            }
        }
    } else {
        ranges + FormatRange(selection.min, selection.max, format)
    }
    return normalizeFormatRanges(updated)
}

internal fun remapFormatRanges(
    ranges: List<FormatRange>,
    replacedRange: TextRange,
    insertedLength: Int,
    textLength: Int,
): List<FormatRange> {
    val replacementStart = replacedRange.min.coerceIn(0, textLength + replacedRange.length)
    val replacementEnd = replacedRange.max
    val delta = insertedLength - replacedRange.length
    val insertedEnd = replacementStart + insertedLength

    fun mapStart(offset: Int): Int = when {
        offset <= replacementStart -> offset
        offset >= replacementEnd -> offset + delta
        else -> replacementStart
    }

    fun mapEnd(offset: Int): Int = when {
        offset <= replacementStart -> offset
        offset >= replacementEnd -> offset + delta
        else -> insertedEnd
    }

    return normalizeFormatRanges(
        ranges.mapNotNull { range ->
            val start = mapStart(range.start).coerceIn(0, textLength)
            val end = mapEnd(range.end).coerceIn(start, textLength)
            range.copy(start = start, end = end).takeIf { start < end }
        },
    )
}

private fun normalizeFormatRanges(ranges: List<FormatRange>): List<FormatRange> =
    DemoTextFormat.entries.flatMap { format ->
        val sorted = ranges
            .filter { it.format == format && it.start < it.end }
            .sortedBy(FormatRange::start)
        buildList {
            sorted.forEach { range ->
                val previous = lastOrNull()
                if (previous != null && range.start <= previous.end) {
                    this[lastIndex] = previous.copy(end = maxOf(previous.end, range.end))
                } else {
                    add(range)
                }
            }
        }
    }

internal fun newlyInsertedText(
    before: String,
    after: String,
    replacedRange: TextRange = TextRange(before.length),
): String? {
    if (before == after) return null

    val safeRange = TextRange(
        replacedRange.min.coerceIn(0, before.length),
        replacedRange.max.coerceIn(0, before.length),
    )
    val insertedLength = after.length - (before.length - safeRange.length)
    if (
        insertedLength > 0 &&
        safeRange.min + insertedLength <= after.length &&
        before.take(safeRange.min) == after.take(safeRange.min) &&
        before.drop(safeRange.max) == after.drop(safeRange.min + insertedLength)
    ) {
        return after.substring(safeRange.min, safeRange.min + insertedLength)
    }

    var prefixLength = 0
    val sharedLength = minOf(before.length, after.length)
    while (
        prefixLength < sharedLength &&
        before[prefixLength] == after[prefixLength]
    ) {
        prefixLength += 1
    }

    var suffixLength = 0
    while (
        suffixLength < before.length - prefixLength &&
        suffixLength < after.length - prefixLength &&
        before[before.lastIndex - suffixLength] == after[after.lastIndex - suffixLength]
    ) {
        suffixLength += 1
    }

    return after
        .substring(prefixLength, after.length - suffixLength)
        .takeIf { it.isNotEmpty() }
}

private fun Context.reportTypedDocumentEntry(value: String) {
    sendBroadcast(
        Intent(THREAD_SDK_ACTION)
            .setPackage(THREAD_APP_PACKAGE)
            .putExtra("type", "field_commit")
            .putExtra("screen", "$WORD_DEMO_PACKAGE/document")
            .putExtra("field", "document-current-entry")
            .putExtra("label", "Document text")
            .putExtra("value", value),
    )
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.observeDoubleTaps(
    onDoubleTap: (Offset) -> Unit,
) {
    var previousTapAt = 0L
    var previousTapPosition: Offset? = null

    awaitEachGesture {
        awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        val up = waitForUpOrCancellation(PointerEventPass.Initial)
            ?: return@awaitEachGesture
        val previousPosition = previousTapPosition
        val elapsed = up.uptimeMillis - previousTapAt
        val delta = previousPosition?.let { up.position - it }
        val maximumDistance = 48.dp.toPx()
        val closeEnough = delta != null &&
            delta.x * delta.x + delta.y * delta.y <= maximumDistance * maximumDistance

        if (elapsed in 40L..350L && closeEnough) {
            up.consume()
            onDoubleTap(up.position)
            previousTapAt = 0L
            previousTapPosition = null
        } else {
            previousTapAt = up.uptimeMillis
            previousTapPosition = up.position
        }
    }
}

@Composable
private fun SelectionStatusBar(
    selectedText: String?,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onComment: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Color.White)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectedText == null) {
            Text(
                "Double-tap a word, then drag the blue handles",
                color = Muted,
                fontSize = 12.sp,
            )
            return
        }

        Text(
            "${selectedText.length} selected",
            color = WordBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        SelectionAction("Cut", onCut)
        SelectionAction("Copy", onCopy)
        SelectionAction("Comment", onComment)
        SelectionAction("Clear", onClear)
    }
}

@Composable
private fun SelectionAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = WordBlue,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .semantics {
                role = Role.Button
                contentDescription = "$label selected text"
            }
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun TopAppBar(
    saveStatus: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSearch: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(WordBlue)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolbarButton(symbol = "‹", label = "Back", onClick = {})
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("Urban green spaces essay", color = Color.White, fontSize = 15.sp)
                Text(saveStatus, color = Color(0xFFDCE8F7), fontSize = 11.sp)
            }
            ToolbarButton(symbol = "↶", label = "Undo", enabled = canUndo, onClick = onUndo)
            ToolbarButton(symbol = "↷", label = "Redo", enabled = canRedo, onClick = onRedo)
            ToolbarButton(symbol = "⌕", label = "Find", onClick = onSearch)
            ToolbarButton(symbol = "⇧", label = "Share", onClick = {})
            ToolbarButton(symbol = "•••", label = "More", onClick = {})
        }
    }
}

@Composable
private fun Ribbon(
    selectionAvailable: Boolean,
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    highlight: Boolean,
    fontColor: Boolean,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onHighlight: () -> Unit,
    onFontColor: () -> Unit,
    onComment: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RibbonBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RibbonButton("Cut", symbol = "✂", enabled = selectionAvailable, onClick = onCut)
        RibbonButton("Copy", symbol = "▣", enabled = selectionAvailable, onClick = onCopy)
        RibbonButton("Paste", symbol = "▤", onClick = onPaste)
        RibbonButton("Select All", symbol = "A", onClick = onSelectAll)
        RibbonButton(
            "Bold",
            symbol = "B",
            enabled = selectionAvailable,
            selected = bold,
            onClick = onBold,
        )
        RibbonButton(
            "Italic",
            symbol = "I",
            enabled = selectionAvailable,
            selected = italic,
            onClick = onItalic,
        )
        RibbonButton(
            "Underline",
            symbol = "U",
            enabled = selectionAvailable,
            selected = underline,
            onClick = onUnderline,
        )
        RibbonButton(
            "Highlight",
            symbol = "H",
            enabled = selectionAvailable,
            selected = highlight,
            onClick = onHighlight,
        )
        RibbonButton(
            "Font Color",
            symbol = "A",
            enabled = selectionAvailable,
            selected = fontColor,
            onClick = onFontColor,
        )
        RibbonButton(
            "New Comment",
            symbol = "+",
            enabled = selectionAvailable,
            onClick = onComment,
        )
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    matchCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 14.sp),
            modifier = Modifier
                .weight(1f)
                .background(RibbonBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .semantics { contentDescription = "Find text in document" },
            decorationBox = { inner ->
                if (value.isEmpty()) Text("Find in document", color = Muted, fontSize = 14.sp)
                inner()
            },
        )
        Spacer(Modifier.width(10.dp))
        Text("$matchCount matches", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun BottomBar(
    activeTab: String,
    wordCount: Int,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(RibbonBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            listOf("Home", "Insert", "Draw", "Layout", "Review", "View").forEach { tab ->
                RibbonButton(
                    label = tab,
                    selected = tab == activeTab,
                    onClick = { onSelect(tab) },
                )
            }
        }
        Text(
            "$wordCount words · Editing · $activeTab tab",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ToolbarButton(
    symbol: String,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 3.dp),
    ) {
        Text(
            symbol,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun RibbonButton(
    label: String,
    symbol: String = label.take(1),
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                if (selected) ActiveControl else Color.White,
                RoundedCornerShape(7.dp),
            )
            .border(1.dp, PageBorder, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = "$label button"
            }
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            symbol,
            color = when {
                !enabled -> Muted.copy(alpha = 0.45f)
                selected -> WordBlue
                else -> Ink
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontStyle = if (label == "Italic") FontStyle.Italic else FontStyle.Normal,
            textDecoration =
                if (label == "Underline") TextDecoration.Underline else TextDecoration.None,
        )
        Text(
            label,
            color = when {
                !enabled -> Muted.copy(alpha = 0.45f)
                selected -> WordBlue
                else -> Muted
            },
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private const val THREAD_SDK_ACTION = "com.thread.sdk.EVENT"
private const val THREAD_APP_PACKAGE = "com.thread.app"
private const val WORD_DEMO_PACKAGE = "com.thread.worddemo"
private const val MAX_TYPED_ENTRY_LENGTH = 120
private const val TYPING_COMMIT_DELAY_MS = 1_200L

private const val SAMPLE_DOCUMENT = """Parks, gardens, and tree-lined streets make cities more pleasant places to live. They provide space for exercise, relaxation, and social activities while offering a welcome break from traffic, buildings, and busy schedules.

Green spaces can also improve the local environment. Trees provide shade on hot days, plants help absorb rainwater, and natural areas create habitats for birds and insects. These benefits can make neighborhoods healthier and more comfortable throughout the year.

Community parks give people places to meet outside their homes. Families can play, neighbors can organize events, and visitors can enjoy quiet time without needing to spend money. Well-maintained public spaces can therefore strengthen connections between people who share a neighborhood.

Cities should include accessible green spaces when planning new development. Investing in parks and trees supports the environment, improves daily life, and creates welcoming public areas that can be enjoyed by people of all ages."""
