package com.thread.lookup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The other app. A directory of budget codes, and nothing else.
 *
 * It exists so the pin scenario is real rather than mimed. The failure being
 * shown is specific to a phone: there is no second window, so a code read here
 * is gone by the time you are back in the form. Someone with an intact working
 * memory carries six characters across the app switch without noticing they did
 * it. Someone without does the trip again. And again.
 *
 * Note what this app does NOT do: it has no knowledge of Thread, imports
 * nothing, and reports nothing. Thread reads the value off the screen through
 * the accessibility tree, which is the Tier 1 story in miniature.
 */
class BudgetCodeLookup : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen() }
    }

    @Composable
    private fun Screen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F8FA))
                .padding(20.dp)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Finance Portal",
                color = Color(0xFF1B1F27),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Budget codes for the current financial year.",
                color = Color(0xFF5B6472),
                fontSize = 13.sp,
            )

            codes.forEach { (label, value) -> CodeRow(label, value) }
        }
    }

    /**
     * Label above value, both as plain text.
     *
     * This shape is what Thread's lookup detector keys on - a recognised label
     * word followed by a short value containing digits. Nothing here is marked
     * up for Thread's benefit; it is just an ordinary list.
     */
    @Composable
    private fun CodeRow(label: String, value: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, color = Color(0xFF5B6472), fontSize = 12.sp)
            Text(value, color = Color(0xFF1B1F27), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }

    private val codes = listOf(
        "Budget code - Engineering Platform" to "BX-4417",
        "Budget code - Engineering Devices" to "BX-4418",
        "Budget code - Sales India" to "BX-2209",
        "Budget code - Marketing Field" to "BX-3310",
        "Budget code - Corporate Services" to "BX-1102",
        "Budget code - Research Applied" to "BX-9014",
    )
}
