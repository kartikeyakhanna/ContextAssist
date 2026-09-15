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
 * Step 1. Short, clear, three fields - design-time Screen Memory Load 18.4.
 *
 * This screen is here to make a point by contrast: the form is not badly built.
 * Nothing that happens later is the fault of this screen, and a demo that opened
 * on a deliberately terrible form would be arguing against a strawman.
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

            Spacer(Modifier.height(8.dp))
            PrimaryButton("Next") {
                startActivity(Intent(this, Step2Allocation::class.java))
            }
        }
    }
}
