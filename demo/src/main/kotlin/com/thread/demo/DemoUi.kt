package com.thread.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Plain, unremarkable form chrome.
 *
 * Kept deliberately ordinary. The demo must not look like a purpose-built prop -
 * if the form looks strange, the audience concludes the problem is the form. The
 * problem is the interruption.
 */

val PageBackground = Color(0xFFF7F8FA)
val Ink = Color(0xFF1B1F27)
val SubtleInk = Color(0xFF5B6472)
val Line = Color(0xFFE3E7ED)

@Composable
fun Page(title: String, step: String?, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .systemBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        step?.let { Text(it, color = SubtleInk, fontSize = 13.sp) }
        Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        color = SubtleInk,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    helper: String? = null,
    error: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color(0xFFB3261E), fontSize = 12.sp) }
        helper?.let { Text(it, color = SubtleInk, fontSize = 12.sp) }
    }
    CommitOnIdle(value, onCommit)
}

/**
 * Reports a commit once typing has settled.
 *
 * A keystroke is not a decision. Treating every character as a committed value
 * would fill the resumption card with half-typed fragments, which is worse than
 * showing nothing - the person would not recognise their own work.
 */
@Composable
private fun CommitOnIdle(value: String, onCommit: () -> Unit) {
    LaunchedEffect(value) {
        if (value.isBlank()) return@LaunchedEffect
        delay(900)
        onCommit()
    }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, color = Ink, fontSize = 15.sp)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}
