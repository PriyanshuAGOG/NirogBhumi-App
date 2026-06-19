package in.nirogbhumi.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import in.nirogbhumi.app.ui.theme.MyApplicationTheme

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyApplicationTheme {
            Column(Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Health Connect permissions", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF182219))
                Spacer(Modifier.height(16.dp))
                Text("Nirog Bhumi imports only the health categories you approve to build your private trends and reports. Imported data is stored in your protected account, is never public, and can be disconnected or deleted. You can change access at any time in Health Connect.", color = Color(0xFF455148))
                Spacer(Modifier.height(24.dp))
                Button(onClick = { finish() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF314936))) { Text("I understand") }
            }
        } }
    }
}
