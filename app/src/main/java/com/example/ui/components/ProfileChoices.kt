package com.nirogbhumi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirogbhumi.app.health.domain.DiabetesType
import com.nirogbhumi.app.health.domain.DiabetesTypes

private val Green = Color(0xFF314936)
private val Ink = Color(0xFF182219)
private val Edge = Color(0xFFD8D0C0)

/**
 * "What type of diabetes, if any?" - one control for onboarding, Profile and family members.
 * Choosing Other reveals a short text box (60 characters) so nobody has to pick a wrong box.
 */
@Composable
fun DiabetesTypePicker(
    selected: DiabetesType?,
    otherText: String,
    onSelect: (DiabetesType) -> Unit,
    onOtherText: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = Ink,
) {
    Column(modifier) {
        Text("Type of diabetes", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = labelColor)
        Spacer(Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiabetesType.entries.forEach { type ->
                val active = type == selected
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .background(if (active) Green else Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, if (active) Green else Edge, RoundedCornerShape(12.dp))
                        .clickable { onSelect(type) }
                        .semantics { role = Role.RadioButton; this.selected = active }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(type.label, color = if (active) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        if (selected == DiabetesType.OTHER) {
            Spacer(Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = otherText,
                onValueChange = { onOtherText(it.take(DiabetesTypes.MAX_OTHER_LENGTH)) },
                label = { Text("Tell us which type (optional)") },
                supportingText = { Text("${otherText.length}/${DiabetesTypes.MAX_OTHER_LENGTH}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
