package com.thread.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
) {
    when (offer) {
        is Offer.Resumption -> ResumptionCard(offer, onAccept, onDismiss, onNever)
        is Offer.Reassurance -> OneLineChip("${offer.consequence}. You can undo for ${offer.undoWindowSeconds / 60} minutes.", onDismiss)
        is Offer.ErrorExplanation -> OneLineChip(offer.message, onDismiss)
        is Offer.DefaultHint -> OneLineChip("${offer.suggestion}. ${offer.reversibility}.", onDismiss)
        is Offer.SequencingMode -> SequencingOffer(offer, onAccept, onDismiss)
        is Offer.Pin -> PinChip(offer.label, offer.value, onDismiss)
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

        Line("Done", offer.done)
        offer.decided?.let { Line("You chose", it) }
        offer.next?.let { Line("Next", it) }

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
