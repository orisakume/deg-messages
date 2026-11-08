package com.example.messages.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.messages.core.design.AppColors

@Composable
fun OnboardingScreen(onRequestDefault: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = AppColors.AccentGreen, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(28.dp))
        Text("Welcome to Messages", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Simple · Private · Fast\nA focused SMS app with a fixed black, white and green design.",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Card(colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceElevated)) {
            Column(Modifier.padding(18.dp)) {
                Text("Make Messages your default SMS app", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android requires the default SMS app to send and receive messages and access SMS conversations. SMS, OTP tools, and Recycle Bin are available. Photo messaging (MMS) is planned for a future version. You can change the default later in Android settings.",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Set as default SMS app")
        }
        Spacer(Modifier.height(12.dp))
        Text("No account, ads, or message uploads.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
