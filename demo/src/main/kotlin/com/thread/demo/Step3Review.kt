package com.thread.demo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thread.sdk.ThreadSdk

/**
 * Step 3. Design-time score 32.6 - low, with one flagged property: submit is
 * irreversible and does not say so.
 *
 * That omission is the entire reason the reassurance chip exists. Hesitating in
 * front of an irreversible button is not indecision; it is the rational response
 * to an unstated consequence, and it is far more costly for someone who cannot
 * cheaply rebuild the context needed to re-check their work.
 *
 * Note that the chip does not wait for a high score. A calm screen with one
 * unstated consequence is exactly where hesitation means something, and gating
 * reassurance behind overall load would have missed it.
 */
class Step3Review : ComponentActivity() {

    private val screen = "com.thread.demo/Step3Review"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen() }
    }

    @Composable
    private fun Screen() {
        // Reported when the screen carrying the irreversible action is reached.
        LaunchedEffect(Unit) {
            ThreadSdk.irreversibleAction(
                context = this@Step3Review,
                screen = screen,
                actionId = "submit",
                consequence = "This goes to your manager for approval",
                undoWindowSeconds = 600,
            )
        }

        Page(title = "Review and submit", step = "Step 3 of 3 - Review") {
            SummaryRow("Purpose", DemoState.purpose)
            SummaryRow("Dates", DemoState.travelDates)
            SummaryRow("Destination", DemoState.destination)
            SummaryRow("Travel class", DemoState.travelClass)
            SummaryRow("Estimated cost", DemoState.estimatedCost)
            SummaryRow("Cost centre", DemoState.costCentre)
            SummaryRow("Budget code", DemoState.budgetCode)
            SummaryRow("Apportionment", DemoState.apportionment)
            SummaryRow("Approver", DemoState.approver)

            Spacer(Modifier.height(16.dp))
            PrimaryButton("Submit request") {
                // Everything Thread was holding is dropped here. Not archived,
                // not summarised - dropped.
                ThreadSdk.taskCompleted(this@Step3Review, screen)
                Toast.makeText(this@Step3Review, "Request submitted", Toast.LENGTH_SHORT).show()
                finishAffinity()
            }
        }
    }

    @Composable
    private fun SummaryRow(label: String, value: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = SubtleInk, fontSize = 14.sp, modifier = Modifier.width(120.dp))
            Text(value.ifBlank { "Not set" }, color = Ink, fontSize = 14.sp)
        }
    }
}
