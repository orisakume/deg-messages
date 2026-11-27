package com.example.messages

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.messages.core.design.MessagesDesign
import com.example.messages.feature.navigation.MessagesRoot
import com.example.messages.platform.NotificationRoutes
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var notificationTarget by mutableStateOf(-1L to "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationRoutes.consume(intent)?.let { notificationTarget = it }
        setContent {
            val target = notificationTarget
            MessagesDesign {
                MessagesRoot(target.first, target.second) {
                    notificationTarget = -1L to ""
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationRoutes.consume(intent)?.let { notificationTarget = it }
    }
}
