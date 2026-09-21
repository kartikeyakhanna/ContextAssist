package com.thread.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thread.sdk.ThreadSdk

/**
 * Step 1. Eight controls, packed into uneven rows - design-time Screen Memory
 * Load 50.2.
 *
 * Two fields on one line, then three on the next. This is the layout that turns
 * a long form into a confusing one: there is no single column to scan down, so
 * the eye has no reliable answer to "where do I look next", and controls end up
 * grouped by what fits rather than by what belongs together. Three across also
 * truncates the labels, which removes the only thing on screen telling you what
 * the box is for.
 *
 * Worth being straight about what this costs. Step 1 used to be the deliberately
 * *well-built* screen, there to show that load is not a synonym for bad design
 * and that Thread is not a workaround for a form somebody should have fixed.
 * That contrast is gone; this screen is now badly laid out and the honest reading
 * is that its first fix is a designer, not an assistive service. What Thread
 * still does here is the thing no layout fixes: carry the place in the task
 * across an interruption.
 *
 * The design-time score does not see any of this. The rubric has no term for
 * layout - it counts items, options, progress, irreversibility, cross-references
 * and language - so the rise to 50.2 comes from two added fields, not from the
 * rows. The scan cost is real and unmeasured, and is recorded in
 * `offendingElements` rather than smuggled into a factor to make the number look
 * right.
 */
class Step1Details : ComponentActivity() {

    private val screen = "com.thread.demo/Step1Details"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fresh run every time it is launched from the home screen, so the demo
        // is repeatable without uninstalling anything.
        if (savedInstanceState == null) DemoState.reset()

        ThreadSdk.startTask(
            context = this,
            intent = "Submitting your Q3 travel request",
            screen = screen,
        )

        setContent { Screen() }
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        var purpose by remember { mutableStateOf(DemoState.purpose) }
        var dates by remember { mutableStateOf(DemoState.travelDates) }
        var destination by remember { mutableStateOf(DemoState.destination) }
        var travelClass by remember { mutableStateOf(DemoState.travelClass) }
        var estimatedCost by remember { mutableStateOf(DemoState.estimatedCost) }
        var currency by remember { mutableStateOf(DemoState.currency) }
        var employeeId by remember { mutableStateOf(DemoState.employeeId) }
        var approver by remember { mutableStateOf(DemoState.approver) }

        Page(title = "Travel request", step = "Step 1 of 3 - Details") {
            FieldRow {
                Field(
                    label = "Purpose of travel",
                    value = purpose,
                    onValueChange = { purpose = it; DemoState.purpose = it },
                    onCommit = {
                        ThreadSdk.fieldCommitted(this@Step1Details, screen, "purpose", "Purpose", purpose)
                    },
                    modifier = Modifier.weight(1f),
                )
                Field(
                    label = "Travel dates",
                    value = dates,
                    onValueChange = { dates = it; DemoState.travelDates = it },
                    onCommit = {
                        ThreadSdk.fieldCommitted(this@Step1Details, screen, "dates", "Dates", dates)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            FieldRow {
                Picker(
                    label = "Travel class",
                    value = travelClass,
                    options = DemoState.travelClasses,
                    onSelect = {
                        travelClass = it
                        DemoState.travelClass = it
                        ThreadSdk.decisionMade(this@Step1Details, screen, "Travel class", it)
                    },
                    modifier = Modifier.weight(1.2f),
                )
                Field(
                    label = "Estimated cost",
                    value = estimatedCost,
                    onValueChange = { estimatedCost = it; DemoState.estimatedCost = it },
                    onCommit = {
                        ThreadSdk.fieldCommitted(
                            this@Step1Details, screen, "estimatedCost", "Estimated cost", estimatedCost,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                Picker(
                    label = "Currency",
                    value = currency,
                    options = DemoState.currencies,
                    onSelect = {
                        currency = it
                        DemoState.currency = it
                        ThreadSdk.decisionMade(this@Step1Details, screen, "Currency", it)
                    },
                    modifier = Modifier.weight(0.9f),
                )
            }

            FieldRow {
                Field(
                    label = "Employee ID",
                    value = employeeId,
                    onValueChange = { employeeId = it; DemoState.employeeId = it },
                    onCommit = {
                        ThreadSdk.fieldCommitted(
                            this@Step1Details, screen, "employeeId", "Employee ID", employeeId,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                Field(
                    label = "Approving manager",
                    value = approver,
                    onValueChange = { approver = it; DemoState.approver = it },
                    onCommit = {
                        ThreadSdk.fieldCommitted(this@Step1Details, screen, "approver", "Approver", approver)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionHeader("Destination")
            listOf("Delhi", "Bengaluru", "Hyderabad").forEach { option ->
                ChoiceRow(
                    label = option,
                    selected = destination == option,
                    onSelect = {
                        destination = option
                        DemoState.destination = option
                        // Reported as a decision, not a commit. This is the line
                        // that comes back as "You chose: Delhi" after the
                        // interruption, and it is the one that stops her
                        // reopening a question she already settled.
                        ThreadSdk.decisionMade(this@Step1Details, screen, "Destination", option)
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            PrimaryButton("Next") {
                startActivity(Intent(this, Step2Allocation::class.java))
            }
        }
    }
}
