package com.example.messages.feature.navigation

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import com.example.messages.feature.schedule.ScheduledSmsScreen
import com.example.messages.feature.settings.PinGate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import com.example.messages.feature.inbox.ConversationAction
import com.example.messages.feature.conversation.MessageAction
import com.example.messages.core.design.restoreActionOrder
import com.example.messages.feature.onboarding.OnboardingScreen
import com.example.messages.feature.otp.OtpScreen
import com.example.messages.feature.otp.OtpBadgeViewModel
import com.example.messages.feature.recyclebin.RecycleBinScreen
import com.example.messages.feature.spam.SpamScreen
import com.example.messages.feature.settings.SettingsScreen
import com.example.messages.feature.settings.SettingsDetailScreen
import com.example.messages.feature.settings.SettingsDestination
import com.example.messages.feature.settings.AboutScreen
import com.example.messages.platform.DefaultSmsRole

private enum class Destination(val route: String, val label: String) {
    Chats("chats", "Chats"), Otps("otps", "OTPs"), Settings("settings", "Settings")
}

private const val ScreenTransitionMs = 220
private val BottomBarHeight = 80.dp

@Composable
fun MessagesRoot(openThreadId: Long = -1L, openAddress: String = "", onOpenTargetConsumed: () -> Unit = {}) {
    PinGate { MessagesNavigation(openThreadId, openAddress, onOpenTargetConsumed) }
}

@Composable
private fun MessagesNavigation(openThreadId: Long, openAddress: String, onOpenTargetConsumed: () -> Unit) {
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

    val appearance = remember(context) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var transparentNavigationBar by remember(appearance) { mutableStateOf(appearance.getBoolean("transparent_bottom_bar", true)) }
    var showChatCategories by remember(appearance) { mutableStateOf(appearance.getBoolean("show_chat_categories", true)) }
    var conversationActionOrder by remember(appearance) {
        mutableStateOf(restoreActionOrder(appearance.getString(ConversationAction.ORDER_KEY, null), ConversationAction.entries))
    }
    var messageActionOrder by remember(appearance) {
        mutableStateOf(restoreActionOrder(appearance.getString(MessageAction.ORDER_KEY, null), MessageAction.entries))
    }
    val navController = rememberNavController()
    var forwardedText by rememberSaveable { mutableStateOf("") }
    var forwardedImage by rememberSaveable { mutableStateOf<String?>(null) }
    var editedInboxThread by remember { mutableStateOf<Pair<Long, Long>?>(null) }
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
    val showBottomBar = Destination.entries.any { it.route == currentRoute }
    var lastTab by remember { mutableStateOf(Destination.Chats.route) }
    LaunchedEffect(currentRoute) {
        if (showBottomBar && currentRoute != null) lastTab = currentRoute
    }
    val navigateToTab: (Destination) -> Unit = { destination ->
        if (navController.currentDestination?.route != destination.route) {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val tabPadding = if (transparentNavigationBar) Modifier else Modifier.padding(bottom = BottomBarHeight)
    val bottomBarSpace = if (transparentNavigationBar) BottomBarHeight else 0.dp
    fun openPage(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }
    Box(
        Modifier.fillMaxSize().background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).clipToBounds(),
    ) {
        NavHost(
            navController,
            startDestination = Destination.Chats.route,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight().widthIn(max = 960.dp).fillMaxWidth(),
            enterTransition = {
                val fade = fadeIn(tween(ScreenTransitionMs))
                if (Destination.entries.any { it.route == targetState.destination.route }) fade
                else fade + slideInHorizontally(tween(ScreenTransitionMs)) { it / 12 }
            },
            exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = {
                val fade = fadeOut(tween(ScreenTransitionMs))
                if (Destination.entries.any { it.route == initialState.destination.route }) fade
                else fade + slideOutHorizontally(tween(ScreenTransitionMs)) { it / 12 }
            },
        ) {
            composable(Destination.Chats.route) {
                Box(Modifier.fillMaxSize().background(AppColors.Background).then(tabPadding)) {
                    InboxScreen(
                        onOpenConversation = { threadId, address ->
                            editedInboxThread = null
                            openPage("conversation/$threadId/${Uri.encode(address)}")
                        },
                        onCompose = { forwardedText = ""; forwardedImage = null; openPage("compose") },
                        onComposeToNumber = { number ->
                            forwardedText = ""
                            forwardedImage = null
                            openPage("compose?recipient=${Uri.encode(number)}")
                        },
                        onOpenRecycleBin = { openPage("recycle_bin") },
                        onOpenSpam = { openPage("spam") },
                        showCategories = showChatCategories,
                        actionOrder = conversationActionOrder,
                        bottomBarSpace = bottomBarSpace,
                        editedThread = editedInboxThread,
                        onEditedThreadShown = { editedInboxThread = null },
                    )
                }
            }
            composable(
                route = "conversation/{threadId}/{address}",
                arguments = listOf(navArgument("threadId") { type = NavType.StringType }, navArgument("address") { type = NavType.StringType }),
            ) { entry ->
                ConversationScreen(
                    actionOrder = messageActionOrder,
                    onBack = { navController.popBackStack() },
                    onForward = { text, image ->
                        forwardedText = text
                        forwardedImage = image
                        navController.navigate("compose")
                    },
                    onMessagesDeleted = { id, latestDate ->
                        if (navController.previousBackStackEntry?.destination?.route == Destination.Chats.route) {
                            editedInboxThread = latestDate?.let { id to it }
                        }
                    },
                )
            }
            composable(
                route = "compose?recipient={recipient}",
                arguments = listOf(navArgument("recipient") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                ComposeScreen(
                    initialRecipient = entry.arguments?.getString("recipient").orEmpty(),
                    forwardedText = forwardedText,
                    forwardedImage = forwardedImage,
                    onBack = { navController.popBackStack() },
                    onConversation = { threadId, address ->
                        forwardedText = ""
                        forwardedImage = null
                        navController.navigate("conversation/$threadId/${Uri.encode(address)}") {
                            popUpTo("compose?recipient={recipient}") { inclusive = true }
                        }
                    },
                )
            }
            composable(Destination.Otps.route) {
                Box(Modifier.fillMaxSize().background(AppColors.Background).then(tabPadding)) { OtpScreen(bottomBarSpace = bottomBarSpace) }
            }
            composable(Destination.Settings.route) {
                Box(Modifier.fillMaxSize().background(AppColors.Background).then(tabPadding)) {
                    SettingsScreen(
                        onOpen = { openPage(it.route) },
                        showChatCategories = showChatCategories,
                        onShowChatCategoriesChange = { enabled ->
                            showChatCategories = enabled
                            appearance.edit().putBoolean("show_chat_categories", enabled).apply()
                        },
                        bottomBarSpace = bottomBarSpace,
                    )
                }
            }
            SettingsDestination.entries.filter { it.hasSettingsPage }.forEach { destination ->
                composable(destination.route) {
                    SettingsDetailScreen(
                        destination = destination,
                        conversationActionOrder = conversationActionOrder,
                        onConversationActionOrderChange = { order ->
                            conversationActionOrder = order
                            appearance.edit().putString(ConversationAction.ORDER_KEY, order.joinToString(",") { it.name }).apply()
                        },
                        messageActionOrder = messageActionOrder,
                        onMessageActionOrderChange = { order ->
                            messageActionOrder = order
                            appearance.edit().putString(MessageAction.ORDER_KEY, order.joinToString(",") { it.name }).apply()
                        },
                        onBack = { navController.popBackStack() },
                        onOpenRecycleBin = { openPage("recycle_bin") },
                        onOpenSpam = { openPage("spam") },
                        transparentNavigationBar = transparentNavigationBar,
                        onTransparentNavigationBarChange = { enabled ->
                            transparentNavigationBar = enabled
                            appearance.edit().putBoolean("transparent_bottom_bar", enabled).apply()
                        },
                    )
                }
            }
            composable("scheduled") { ScheduledSmsScreen { navController.popBackStack() } }
            composable("about") { AboutScreen(onBack = { navController.popBackStack() }) }
            composable("recycle_bin") { RecycleBinScreen(onBack = { navController.popBackStack() }) }
            composable("spam") { SpamScreen(onBack = { navController.popBackStack() }) }
        }
        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(ScreenTransitionMs)),
            exit = fadeOut(tween(ScreenTransitionMs)),
        ) {
            MessagesBottomBar(if (showBottomBar) currentRoute else lastTab, showBottomBar, transparentNavigationBar, navigateToTab)
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
private fun MessagesBottomBar(selectedRoute: String?, enabled: Boolean, transparent: Boolean, onSelect: (Destination) -> Unit) {
    NavigationBar(
        modifier = Modifier.height(BottomBarHeight),
        windowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (transparent) AppColors.Surface.copy(alpha = 0.76f) else AppColors.Surface,
        tonalElevation = if (transparent) 0.dp else NavigationBarDefaults.Elevation,
    ) {
        Destination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selectedRoute == destination.route,
                enabled = enabled,
                onClick = { onSelect(destination) },
                icon = {
                    when (destination) {
                        Destination.Chats -> Icon(Icons.Default.ChatBubbleOutline, contentDescription = destination.label)
                        Destination.Otps -> OtpTabIcon(destination.label)
                        Destination.Settings -> Icon(Icons.Default.Settings, contentDescription = destination.label)
                    }
                },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppColors.AccentGreen,
                    selectedTextColor = AppColors.AccentGreen,
                    unselectedIconColor = AppColors.TextSecondary,
                    unselectedTextColor = AppColors.TextSecondary,
                    disabledIconColor = AppColors.TextSecondary,
                    disabledTextColor = AppColors.TextSecondary,
                    indicatorColor = AppColors.GreenTint,
                ),
            )
        }
    }
}

@Composable
private fun OtpTabIcon(label: String, viewModel: OtpBadgeViewModel = hiltViewModel()) {
    val count by viewModel.count.collectAsStateWithLifecycle()
    BadgedBox(badge = {
        if (count > 0) Badge(containerColor = AppColors.AccentGreen, contentColor = AppColors.Background) {
            Text(if (count > 99) "99+" else count.toString())
        }
    }) { Icon(Icons.Default.VerifiedUser, contentDescription = label) }
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
