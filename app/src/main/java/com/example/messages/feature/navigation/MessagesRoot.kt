package com.example.messages.feature.navigation

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.messages.MainActivity
import com.example.messages.core.design.AppColors
import com.example.messages.feature.conversation.ComposeScreen
import com.example.messages.feature.conversation.ConversationScreen
import com.example.messages.feature.inbox.InboxScreen
import com.example.messages.feature.onboarding.OnboardingScreen
import com.example.messages.feature.otp.OtpScreen
import com.example.messages.feature.otp.OtpBadgeViewModel
import com.example.messages.feature.recyclebin.RecycleBinScreen
import com.example.messages.feature.spam.SpamScreen
import com.example.messages.feature.settings.SettingsScreen
import com.example.messages.feature.settings.AboutScreen
import com.example.messages.platform.DefaultSmsRole

private enum class Destination(val route: String, val label: String) {
    Chats("chats", "Chats"), Otps("otps", "OTPs"), Settings("settings", "Settings")
}

@Composable
fun MessagesRoot(openThreadId: Long = -1L, openAddress: String = "", onOpenTargetConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    var isDefaultSmsApp by remember { mutableStateOf(DefaultSmsRole.isHeld(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isDefaultSmsApp = DefaultSmsRole.isHeld(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultSmsApp = DefaultSmsRole.isHeld(context)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val prompts = remember { context.getSharedPreferences("runtime_prompts", Context.MODE_PRIVATE) }
    var showNotificationPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(isDefaultSmsApp) {
        if (isDefaultSmsApp && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prompts.getBoolean("notification_prompted", false)
        ) showNotificationPrompt = true
    }

    if (!isDefaultSmsApp) {
        OnboardingScreen(onRequestDefault = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                    roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                }
            } else {
                @Suppress("DEPRECATION")
                val request = Intent("android.provider.Telephony.ACTION_CHANGE_DEFAULT")
                    .putExtra("package", context.packageName)
                roleLauncher.launch(request)
            }
        })
        return
    }

    val navController = rememberNavController()
    val otpBadgeViewModel: OtpBadgeViewModel = hiltViewModel()
    val otpCount by otpBadgeViewModel.count.collectAsStateWithLifecycle()
    LaunchedEffect(openThreadId, openAddress) {
        if (openThreadId >= 0) {
            navController.navigate("conversation/$openThreadId/${Uri.encode(openAddress)}") {
                launchSingleTop = true
            }
            onOpenTargetConsumed()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val navigateToTab: (Destination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(
        containerColor = AppColors.Background,
        bottomBar = {
            if (currentRoute in Destination.entries.map(Destination::route)) {
                NavigationBar(containerColor = AppColors.Surface) {
                    Destination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(destination) },
                            icon = {
                                val image = when (destination) {
                                    Destination.Chats -> Icons.Default.ChatBubbleOutline
                                    Destination.Otps -> Icons.Default.VerifiedUser
                                    Destination.Settings -> Icons.Default.Settings
                                }
                                if (destination == Destination.Otps && otpCount > 0) {
                                    BadgedBox(badge = {
                                        Badge(containerColor = AppColors.AccentGreen, contentColor = AppColors.Background) {
                                            Text(if (otpCount > 99) "99+" else otpCount.toString())
                                        }
                                    }) { Icon(image, contentDescription = destination.label) }
                                } else Icon(image, contentDescription = destination.label)
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppColors.AccentGreen,
                                selectedTextColor = AppColors.AccentGreen,
                                unselectedIconColor = AppColors.TextSecondary,
                                unselectedTextColor = AppColors.TextSecondary,
                                indicatorColor = AppColors.GreenTint,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = Destination.Chats.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Chats.route) {
                InboxScreen(
                    onOpenConversation = { threadId, address -> navController.navigate("conversation/$threadId/${Uri.encode(address)}") },
                    onCompose = { navController.navigate("compose") },
                    onOpenRecycleBin = { navController.navigate("recycle_bin") },
                    onOpenSpam = { navController.navigate("spam") },
                )
            }
            composable(
                route = "conversation/{threadId}/{address}",
                arguments = listOf(navArgument("threadId") { type = NavType.StringType }, navArgument("address") { type = NavType.StringType }),
            ) { entry ->
                ConversationScreen(onBack = { navController.popBackStack() })
            }
            composable("compose") {
                ComposeScreen(
                    onBack = { navController.popBackStack() },
                    onConversation = { threadId, address ->
                        navController.navigate("conversation/$threadId/${Uri.encode(address)}") {
                            popUpTo("compose") { inclusive = true }
                        }
                    },
                )
            }
            composable(Destination.Otps.route) { OtpScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onRecycleBin = { navController.navigate("recycle_bin") },
                    onSpam = { navController.navigate("spam") },
                    onAbout = { navController.navigate("about") },
                )
            }
            composable("about") { AboutScreen(onBack = { navController.popBackStack() }) }
            composable("recycle_bin") { RecycleBinScreen(onBack = { navController.popBackStack() }) }
            composable("spam") { SpamScreen(onBack = { navController.popBackStack() }) }
        }
    }

    if (showNotificationPrompt) {
        AlertDialog(
            onDismissRequest = { prompts.edit().putBoolean("notification_prompted", true).apply(); showNotificationPrompt = false },
            title = { Text("Message notifications") },
            text = { Text("Allow notifications so new SMS messages can alert you when Messages is closed. Android controls lock-screen privacy and notification styling.") },
            confirmButton = {
                TextButton(onClick = {
                    prompts.edit().putBoolean("notification_prompted", true).apply()
                    showNotificationPrompt = false
                    if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { prompts.edit().putBoolean("notification_prompted", true).apply(); showNotificationPrompt = false }) { Text("Not now") }
            },
            containerColor = AppColors.SurfaceElevated,
        )
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(description, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
    }
}
