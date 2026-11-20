package com.example.messages.feature.settings

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.messages.core.design.AppColors
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PinStore(context: Context) {
    val prefs = context.getSharedPreferences("pin_lock", Context.MODE_PRIVATE)
    private val notifications = context.getSystemService(android.app.NotificationManager::class.java)
    val enabled get() = prefs.contains("verifier")
    val password get() = prefs.getBoolean("password", false)
    val fingerprint get() = prefs.getBoolean("fingerprint", false)

    fun check(pin: String): String? {
        val now = System.currentTimeMillis()
        if (now < prefs.getLong("blocked_until", 0)) return "Too many attempts. Try again in a minute."
        val verifier = prefs.getString("verifier", null) ?: return "PIN lock is off."
        if (MessageDigest.isEqual(Base64.decode(verifier, Base64.NO_WRAP), sign(pin, false))) {
            prefs.edit().remove("attempts").remove("blocked_until").commit()
            return null
        }
        val attempts = prefs.getInt("attempts", 0) + 1
        val update = prefs.edit().putInt("attempts", attempts % 5)
        if (attempts >= 5) update.putLong("blocked_until", now + 60_000)
        update.commit()
        return if (attempts >= 5) "Too many attempts. Try again in a minute." else if (password) "Incorrect password." else "Incorrect PIN."
    }

    fun set(pin: String?, usePassword: Boolean = false, useFingerprint: Boolean = false) {
        val update = prefs.edit().clear()
        if (pin != null) update.putString("verifier", Base64.encodeToString(sign(pin, true), Base64.NO_WRAP))
            .putBoolean("password", usePassword).putBoolean("fingerprint", useFingerprint)
        check(update.commit()) { "Could not save the PIN setting." }
        if (pin != null) notifications?.activeNotifications?.filter {
            it.notification.channelId == com.example.messages.MessagesApplication.MESSAGE_CHANNEL_ID
        }?.forEach { notifications.cancel(it.tag, it.id) }
    }

    private fun sign(pin: String, create: Boolean): ByteArray {
        val alias = "deg_messages_pin"
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keys.getKey(alias, null) as? SecretKey ?: run {
            check(create) { "The PIN key is unavailable." }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
                generateKey()
            }
        }
        return Mac.getInstance("HmacSHA256").run { init(key); doFinal(pin.toByteArray(Charsets.UTF_8)) }
    }
}

@Composable
internal fun PinGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val store = remember(context) { PinStore(context) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var locked by remember { mutableStateOf(enabled) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, store) {
        var stoppedAt: Long? = null
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            enabled = store.enabled
            if (!enabled) locked = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> stoppedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> {
                    val stopped = stoppedAt
                    if (store.enabled && stopped != null && SystemClock.elapsedRealtime() - stopped >= 30_000) locked = true
                    stoppedAt = null
                }
                else -> Unit
            }
        }
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            store.prefs.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    SideEffect {
        if (enabled) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    val stateHolder = rememberSaveableStateHolder()
    if (!locked) {
        stateHolder.SaveableStateProvider("messages") { content() }
        return
    }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp).imePadding(), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Unlock DEG Messages", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            CredentialField(if (store.password) "Password" else "PIN", pin, store.password) { pin = it; error = null }
            error?.let { Text(it, color = AppColors.Danger) }
            Button(enabled = !busy && pin.length >= 4, onClick = {
                busy = true
                scope.launch {
                    error = withContext(Dispatchers.IO) { runCatching { store.check(pin) }.getOrElse { "Could not verify the PIN." } }
                    pin = ""
                    locked = error != null
                    busy = false
                }
            }) { Text("Unlock") }
            if (store.fingerprint) FingerprintButton("Unlock with fingerprint", enabled = !busy) { locked = false }
        }
    }
}

@Composable
internal fun PinLockSetting() {
    val context = LocalContext.current
    val store = remember(context) { PinStore(context) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var dialog by remember { mutableStateOf(false) }
    SettingsLink(
        title = if (enabled) "Manage app lock" else "Set up app lock",
        subtitle = if (enabled) "Change your verification method" else "Protect your chats with a PIN or password",
        icon = SettingsDestination.AppLock.icon,
        accent = SettingsDestination.AppLock.accent,
        onClick = { dialog = true },
    )
    if (!dialog) return
    var current by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var usePassword by remember { mutableStateOf(store.password) }
    var useFingerprint by remember { mutableStateOf(store.fingerprint) }
    val hasFingerprint = fingerprintAvailable(context)
    fun save(remove: Boolean) {
        busy = true
        scope.launch {
            error = withContext(Dispatchers.IO) {
                runCatching {
                    val failure = if (enabled) store.check(current) else null
                    if (failure == null) store.set(if (remove) null else pin, usePassword, useFingerprint && hasFingerprint)
                    failure
                }.getOrElse { "Could not save the PIN setting." }
            }
            if (error == null) { enabled = store.enabled; dialog = false }
            busy = false
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) dialog = false },
        title = { Text("App lock") },
        text = {
            Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (enabled) CredentialField(if (store.password) "Current password" else "Current PIN", current, store.password) { current = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !usePassword, onClick = { usePassword = false; pin = ""; repeat = "" }, label = { Text("PIN") })
                    FilterChip(selected = usePassword, onClick = { usePassword = true; pin = ""; repeat = "" }, label = { Text("Password") })
                }
                CredentialField(if (usePassword) "New password (at least 8 characters)" else "New PIN (4–12 digits)", pin, usePassword) { pin = it }
                CredentialField(if (usePassword) "Confirm password" else "Confirm PIN", repeat, usePassword) { repeat = it }
                if (hasFingerprint) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useFingerprint, onCheckedChange = { useFingerprint = it })
                    Text("Allow fingerprint unlock")
                }
                Text("Locks on launch and after 30 seconds in the background.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = AppColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && pin.length >= (if (usePassword) 8 else 4) && !pin.isBlank() && pin == repeat && (!enabled || current.length >= 4),
                onClick = { save(false) }) { Text("Save lock") }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = { dialog = false }) { Text("Cancel") }
                if (enabled) TextButton(enabled = !busy && current.length >= 4, onClick = { save(true) }) { Text("Remove lock") }
            }
        },
    )
}

@Composable
internal fun CredentialField(label: String, pin: String, password: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(pin, { onChange(if (password) it.take(128) else it.filter { digit -> digit in '0'..'9' }.take(12)) },
        label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.NumberPassword))
}
