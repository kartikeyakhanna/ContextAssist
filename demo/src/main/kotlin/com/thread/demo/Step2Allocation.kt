package com.thread.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thread.sdk.ThreadSdk

/**
 * Step 2. Design-time Screen Memory Load 83.1 - the highest in the cache.
 *
 * Every property the offline agent flagged is real and present here: twenty-five
 * cost centres with no default and no search, a budget code that can only be
 * found in another app, four sections with no indication of which are mandatory,
 * and "apportionment basis" left undefined on screen.
 *
 * This screen is where the interruption lands in the demo, and that is not
 * arbitrary. Context loss peaks in the middle of a task - far enough in that
 * there is something to lose, not far enough that it is nearly done.
 */
class Step2Allocation : ComponentActivity() {

    private val screen = "com.thread.demo/Step2Allocation"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen() }
    }

    @Composable
    private fun Screen() {
        var costCentre by remember { mutableStateOf(DemoState.costCentre) }
        var budgetCode by remember { mutableStateOf(DemoState.budgetCode) }
        var apportionment by remember { mutableStateOf(DemoState.apportionment) }
        var attachments by remember { mutableStateOf(DemoState.attachments) }
        var budgetCodeError by remember { mutableStateOf<String?>(null) }

        Page(title = "Cost allocation", step = null) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                SectionHeader("1. Cost centre")
                DemoState.costCentres.forEach { option ->
                    ChoiceRow(
                        label = option,
                        selected = costCentre == option,
                        onSelect = {
                            costCentre = option
                            DemoState.costCentre = option
                            ThreadSdk.decisionMade(this@Step2Allocation, screen, "Cost centre", option)
                        },
                    )
                }

                SectionHeader("2. Budget code")
                Field(
                    label = "Budget code",
                    value = budgetCode,
                    onValueChange = {
                        budgetCode = it
                        DemoState.budgetCode = it
                        budgetCodeError = null
                    },
                    onCommit = {
                        ThreadSdk.fieldCommitted(
                            this@Step2Allocation, screen, "budgetCode", "Budget code", budgetCode,
                        )
                    },
                    helper = "Find your budget code in the Finance Portal.",
                    error = budgetCodeError,
                )

                SectionHeader("3. Apportionment basis")
                DemoState.apportionmentOptions.forEach { option ->
                    ChoiceRow(
                        label = option,
                        selected = apportionment == option,
                        onSelect = {
                            apportionment = option
                            DemoState.apportionment = option
                            ThreadSdk.fieldCommitted(
                                this@Step2Allocation, screen, "apportionment", "Apportionment", option,
                            )
                        },
                    )
                }

                SectionHeader("4. Supporting documents")
                Field(
                    label = "Attachment reference",
                    value = attachments,
                    onValueChange = { attachments = it; DemoState.attachments = it },
                    onCommit = {
                        ThreadSdk.fieldCommitted(
                            this@Step2Allocation, screen, "attachments", "Attachments", attachments,
                        )
                    },
                )

                Spacer(Modifier.height(12.dp))
                PrimaryButton("Review") {
                    if (budgetCode.isBlank()) {
                        budgetCodeError = "Budget code is required."
                        // Reported so repeated failures at the same wall are
                        // recognised as one obstacle rather than four events.
                        ThreadSdk.validationFailed(
                            this@Step2Allocation, screen, "budgetCode", "Budget code is required.",
                        )
                    } else {
                        startActivity(Intent(this@Step2Allocation, Step3Review::class.java))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
