package in.nirogbhumi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import in.nirogbhumi.app.data.CloudResult
import in.nirogbhumi.app.ui.NirogState
import kotlinx.coroutines.delay

@Composable
fun WalkTimerScreen(state: NirogState) {
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var running by rememberSaveable { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(running) { while (running) { delay(1000); seconds++ } }
    val minutes = seconds / 60; val remainder = seconds % 60

    Column(Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.currentScreen = "walking_overview" }) { Icon(Icons.Outlined.ArrowBack, "Back") }
            Text("Post-dinner walk", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Color(0xFF182219))
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(260.dp).background(Color(0xFFDDE7FA), CircleShape), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d:%02d".format(minutes, remainder), fontFamily = FontFamily.Monospace, fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Color(0xFF182219))
                Text(if (running) "Walking" else "Paused", color = Color(0xFF526057))
            }
        }
        error?.let { Text(it, color = Color(0xFF8B3E36), modifier = Modifier.padding(top = 18.dp)) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { running = !running }, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp)) { Text(if (running) "Pause" else "Resume") }
            Button(onClick = {
                running = false; saving = true
                state.repository.addHealthLog("walkLogs", mapOf("minutes" to maxOf(1, minutes), "seconds" to seconds, "mealRelation" to "after_dinner", "measuredAt" to FieldValue.serverTimestamp(), "source" to "timer")) { result ->
                    saving = false
                    if (result is CloudResult.Success) state.currentScreen = "walking_overview" else error = (result as CloudResult.Failure).message
                }
            }, enabled = !saving, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936))) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("Finish")
            }
        }
        TextButton(onClick = { state.currentScreen = "walking_overview" }) { Text("Cancel", color = Color(0xFF6D746E)) }
    }
}
