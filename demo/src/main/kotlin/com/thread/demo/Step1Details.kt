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
 * Step 1. Six fields, all of them plainly built - design-time Screen Memory
 * Load 38.3.
 *
 * This screen is here to make a point by contrast, and the contrast is not
 * "short versus long". Every label here says what it means, nothing has to be
 * looked up in another system, nothing appears after you answer something else,
 * and the progress indicator is honest. It is a *good* form. It is also six
 * things to hold, which is most of what working memory has, and that is why it
 * scores 38 rather than 18.
 *
 * That distinction is the whole argument. Load is not a synonym for bad design.
 * A demo whose first screen were deliberately terrible would be arguing against
 * a strawman, and the fix would obviously be "build the form better" - which is
 * a fix Thread does not offer and does not need to. What Thread carries is the
 * place in the task, and losing your place in a well-built form is just as
 * expensive as losing it in a badly built one.
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
        var approver by remember { mutableStateOf(DemoState.approver) }

        Page(title = "Travel request", step = "Step 1 of 3 - Details") {
            Field(
                label = "Purpose of travel",
                value = purpose,
                onValueChange = { purpose = it; DemoState.purpose = it },
                onCommit = {
                    ThreadSdk.fieldCommitted(this, screen, "purpose", "Purpose", purpose)
                },
            )

            Field(
                label = "Travel dates",
                value = dates,
                onValueChange = { dates = it; DemoState.travelDates = it },
                onCommit = {
                    ThreadSdk.fieldCommitted(this, screen, "dates", "Dates", dates)
                },
            )

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
                        ThreadSdk.decisionMade(this, screen, "Destination", option)
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            Picker(
                label = "Travel class",
                value = travelClass,
                options = DemoState.travelClasses,
                onSelect = {
                    travelClass = it
                    DemoState.travelClass = it
                    ThreadSdk.decisionMade(this, screen, "Travel class", it)
                },
            )

            Field(
                label = "Estimated cost",
                value = estimatedCost,
                onValueChange = { estimatedCost = it; DemoState.estimatedCost = it },
                onCommit = {
                    ThreadSdk.fieldCommitted(
                        this, screen, "estimatedCost", "Estimated cost", estimatedCost,
                    )
                },
            )

            Field(
                label = "Approving manager",
                value = approver,
                onValueChange = { approver = it; DemoState.approver = it },
                onCommit = {
                    ThreadSdk.fieldCommitted(this, screen, "approver", "Approver", approver)
                },
            )

            Spacer(Modifier.height(8.dp))
            PrimaryButton("Next") {
                startActivity(Intent(this, Step2Allocation::class.java))
            }
        }
    }
}
