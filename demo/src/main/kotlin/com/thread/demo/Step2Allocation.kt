package com.thread.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thread.sdk.ThreadSdk

/**
 * Step 2. The screen the interruption lands on, and the hardest one in the form.
 *
 * Every difficulty here is one that exists in real expense systems, and that
 * matters more than it sounds: a demo that invents a deliberately terrible form
 * is arguing against a strawman, and anyone in the room who has used Concur or
 * SAP will know it. So the hard parts are borrowed rather than designed -
 *
 *  - a cost-centre picker with twenty-five options, unsorted, no search, no
 *    default, and nothing visible until it is opened;
 *  - two code fields that look alike and are fetched from different systems, so
 *    knowing one tells you nothing about the other;
 *  - a field that appears only once "Fixed percentage" is chosen, so how much
 *    work is left cannot be read off the screen;
 *  - one field that is optional and gives no sign of it;
 *  - validation that arrives one error at a time, so fixing the first reveals
 *    the second.
 *
 * All of it is deterministic. Nothing is randomised, because a form that varies
 * per run is a demo that can fail on stage for reasons nobody can reproduce, and
 * an audience that sees two different forms concludes the software is unreliable
 * rather than that the form is hard.
 *
 * The controls are compact on purpose. Twenty-five stacked radio buttons pushed
 * every field below the fold, and Compose does not put clipped content in the
 * accessibility tree at all - measured, forty-four nodes were reachable on a
 * screen holding far more than forty-four controls. Thread could therefore see
 * no fields at all, concluded the screen was not a form, and offered no steps. A
 * picker is both closer to the real system and readable in one screenful.
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
        var glAccount by remember { mutableStateOf(DemoState.glAccount) }
        var apportionment by remember { mutableStateOf(DemoState.apportionment) }
        var percentageSplit by remember { mutableStateOf(DemoState.percentageSplit) }
        var wbsElement by remember { mutableStateOf(DemoState.wbsElement) }
        var attachments by remember { mutableStateOf(DemoState.attachments) }

        var budgetCodeError by remember { mutableStateOf<String?>(null) }
        var glAccountError by remember { mutableStateOf<String?>(null) }
        var splitError by remember { mutableStateOf<String?>(null) }

        Page(title = "Cost allocation", step = null) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Picker(
                    label = "Cost centre",
                    value = costCentre,
                    options = DemoState.costCentres,
                    onSelect = {
                        costCentre = it
                        DemoState.costCentre = it
                        ThreadSdk.decisionMade(this@Step2Allocation, screen, "Cost centre", it)
                    },
                )

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

                // Deliberately next to the budget code and deliberately similar.
                // Two codes that read alike but come from different systems is
                // the commonest way people lose their place in a finance form.
                Field(
                    label = "GL account code",
                    value = glAccount,
                    onValueChange = {
                        glAccount = it
                        DemoState.glAccount = it
                        glAccountError = null
                    },
                    onCommit = {
                        ThreadSdk.fieldCommitted(
                            this@Step2Allocation, screen, "glAccount", "GL account", glAccount,
                        )
                    },
                    helper = "8 digits. Not the same as the budget code.",
                    error = glAccountError,
                )

                Picker(
                    label = "Apportionment basis",
                    value = apportionment,
                    options = DemoState.apportionmentOptions,
                    onSelect = {
                        apportionment = it
                        DemoState.apportionment = it
                        if (it != DemoState.SPLIT_TRIGGER) {
                            percentageSplit = ""
                            DemoState.percentageSplit = ""
                            splitError = null
                        }
                        ThreadSdk.fieldCommitted(
                            this@Step2Allocation, screen, "apportionment", "Apportionment", it,
                        )
                    },
                )

                // Appears only once "Fixed percentage" is chosen.
                if (DemoState.requiresSplit()) {
                    Field(
                        label = "Percentage split",
                        value = percentageSplit,
                        onValueChange = {
                            percentageSplit = it
                            DemoState.percentageSplit = it
                            splitError = null
                        },
                        onCommit = {
                            ThreadSdk.fieldCommitted(
                                this@Step2Allocation, screen, "percentageSplit", "Split", percentageSplit,
                            )
                        },
                        error = splitError,
                    )
                }

                // Optional, and nothing on the screen says so.
                Field(
                    label = "Project / WBS element",
                    value = wbsElement,
                    onValueChange = { wbsElement = it; DemoState.wbsElement = it },
                    onCommit = {
                        ThreadSdk.fieldCommitted(
                            this@Step2Allocation, screen, "wbsElement", "WBS element", wbsElement,
                        )
                    },
                )

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
                    val failure = firstProblem(budgetCode, glAccount, percentageSplit)
                    if (failure == null) {
                        startActivity(Intent(this@Step2Allocation, Step3Review::class.java))
                    } else {
                        when (failure.field) {
                            "budgetCode" -> budgetCodeError = failure.message
                            "glAccount" -> glAccountError = failure.message
                            else -> splitError = failure.message
                        }
                        // Reported so repeated failures at the same wall are
                        // recognised as one obstacle rather than four events.
                        ThreadSdk.validationFailed(
                            this@Step2Allocation, screen, failure.field, failure.message,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    private data class Problem(val field: String, val message: String)

    /**
     * One error at a time, in a fixed order.
     *
     * Reporting every problem at once would be the kinder design, which is
     * exactly why this does not: the form being demonstrated is the one people
     * actually use, and that form makes you press the button again to find out
     * what else is wrong.
     */
    private fun firstProblem(budgetCode: String, glAccount: String, split: String): Problem? = when {
        budgetCode.isBlank() ->
            Problem("budgetCode", "Budget code is required.")
        glAccount.isBlank() ->
            Problem("glAccount", "GL account code is required.")
        !glAccount.matches(Regex("\\d{8}")) ->
            Problem("glAccount", "GL account code must be 8 digits.")
        DemoState.requiresSplit() && split.isBlank() ->
            Problem("percentageSplit", "Percentage split is required for a fixed percentage.")
        else -> null
    }
}
