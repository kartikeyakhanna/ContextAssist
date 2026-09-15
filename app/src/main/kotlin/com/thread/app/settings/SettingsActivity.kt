package com.thread.app.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The opt-in, and the ethical anchor of the whole product.
 *
 * Thread never detects, infers or diagnoses a disability. Screen readers do not
 * detect blindness and captions do not detect deafness - real assistive technology
 * is declared, not diagnosed. The behavioural signals only decide *when* support
 * appears; they play no part in deciding *whether* somebody needs it.
 *
 * This screen cannot enable the service itself. Android requires the user to turn
 * it on in Settings > Accessibility, which means the consent model is enforced by
 * the platform rather than promised by us.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(
                onOpenAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
        }
    }
}

private val Bg = Color(0xFF141A22)
private val Surface = Color(0xFF1F2733)
private val OnSurface = Color(0xFFF2F4F7)
private val Muted = Color(0xFF9AA5B4)
private val Accent = Color(0xFF7FB3D5)

@Composable
private fun SettingsScreen(onOpenAccessibilitySettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Thread", color = OnSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Keeps your place, so you don't have to.",
            color = Muted,
            fontSize = 16.sp,
        )

        Text(
            "Turn on support",
            color = Accent,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(Surface, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenAccessibilitySettings)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        )

        Column(
            modifier = Modifier
                .background(Surface, RoundedCornerShape(12.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("What Thread does with your data", color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Privacy("Everything is worked out on this device. Nothing is sent anywhere.")
            Privacy("Nothing is saved. When a task ends, it is forgotten completely.")
            Privacy("There is no account and no profile. Thread does not know who you are.")
            Privacy("No one else can see any of this - not your employer, not your manager.")
            Privacy("Thread never decides that you need help. You turned it on; you can turn it off.")
        }
    }
}

@Composable
private fun Privacy(text: String) {
    Text("·  $text", color = Muted, fontSize = 14.sp)
}
