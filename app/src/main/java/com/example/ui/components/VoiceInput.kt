package com.nirogbhumi.app.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Launches the system speech-recognition UI (Google's "Speak now" popup on
 * virtually every real device) and hands back the top transcript. Needs no
 * new manifest permission beyond the RECORD_AUDIO already declared for
 * Health Connect - ACTION_RECOGNIZE_SPEECH delegates the actual microphone
 * access to the recognizer app itself, not this app's process.
 *
 * Returns a single lambda to call on tap. [onUnavailable] fires only when no
 * app on the device can handle the intent at all (rare - e.g. a stripped
 * Android build with no Google app) so the caller can show a plain "voice
 * entry isn't available on this device" message instead of silently doing
 * nothing.
 */
@Composable
fun rememberVoiceInputLauncher(
    prompt: String = "Say the reading",
    onResult: (String) -> Unit,
    onUnavailable: () -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (heard != null) onResult(heard)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            launcher.launch(intent)
        } else {
            onUnavailable()
        }
    }
}

// The system recognizer already normalizes a spoken number into digit form
// in its transcript (e.g. "one ten" comes back as "110"), so a single regex
// for the first number in the utterance covers real speech, not just a
// literal "one one zero" readout.
private val FIRST_NUMBER = Regex("\\d+(?:\\.\\d+)?")
private val TWO_NUMBERS = Regex("(\\d{2,3})\\D+(\\d{2,3})")

/** Extracts the first number heard, e.g. "fasting sugar 110" -> "110". */
fun parseSpokenNumber(heard: String): String? = FIRST_NUMBER.find(heard)?.value

/** Extracts two numbers heard in order, e.g. "120 over 80" -> 120 to 80. */
fun parseSpokenTwoNumbers(heard: String): Pair<Int, Int>? {
    val match = TWO_NUMBERS.find(heard) ?: return null
    val first = match.groupValues[1].toIntOrNull() ?: return null
    val second = match.groupValues[2].toIntOrNull() ?: return null
    return first to second
}
