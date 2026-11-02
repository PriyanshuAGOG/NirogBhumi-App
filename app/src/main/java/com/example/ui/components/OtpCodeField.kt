package com.nirogbhumi.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Keeps only the digits of whatever was typed or pasted ("123 456", "Your code is 123456"), at most [length] of them. */
fun cleanOtp(raw: String, length: Int = 6): String = raw.filter { it.isDigit() }.take(length)

/**
 * One code field drawn as separate boxes. One keyboard stays open and the cursor moves on by itself, so the code can be typed
 * straight through, pasted whole (from the keyboard's clipboard suggestion), or filled by the phone. [onComplete] fires the
 * moment the last digit arrives, so no extra tap is needed.
 */
@Composable
fun OtpCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
    enabled: Boolean = true,
    onComplete: (String) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val ink = Color(0xFF182219)
    val green = Color(0xFF314936)
    val line = Color(0xFFD8D0C0)
    val red = Color(0xFFB3261E)

    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val digits = cleanOtp(raw, length)
            if (digits != value) {
                onValueChange(digits)
                if (digits.length == length) onComplete(digits)
            }
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = "$length-digit code. ${value.length} of $length entered." },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (value.length == length) onComplete(value) }),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled) {
                        runCatching { focusRequester.requestFocus() }
                        keyboard?.show()
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(length) { i ->
                    val active = enabled && i == value.length.coerceAtMost(length - 1)
                    Surface(
                        modifier = Modifier.weight(1f, fill = false).size(width = 44.dp, height = 56.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8F6EF),
                        border = BorderStroke(if (active) 2.dp else 1.dp, if (isError) red else if (active) green else line),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text(
                                text = value.getOrNull(i)?.toString().orEmpty(),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
    )
}
