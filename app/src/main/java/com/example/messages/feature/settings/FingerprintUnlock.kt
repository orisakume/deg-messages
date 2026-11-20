package com.example.messages.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Suppress("DEPRECATION")
internal fun fingerprintAvailable(context: Context): Boolean {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return false
    val manager = context.getSystemService(BiometricManager::class.java) ?: return false
    val result = if (Build.VERSION.SDK_INT >= 30) manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        else manager.canAuthenticate()
    return result == BiometricManager.BIOMETRIC_SUCCESS
}

@Composable
internal fun FingerprintButton(label: String, enabled: Boolean = true, onVerified: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activeSignal = remember { mutableStateOf<CancellationSignal?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val verified by rememberUpdatedState(onVerified)
    fun cancel() {
        activeSignal.value?.cancel()
        activeSignal.value = null
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cancel() }
        lifecycle.addObserver(observer)
        onDispose { cancel(); lifecycle.removeObserver(observer) }
    }
    if (fingerprintAvailable(context)) TextButton(enabled = enabled && activeSignal.value == null, onClick = {
        val signal = CancellationSignal()
        activeSignal.value = signal
        val builder = BiometricPrompt.Builder(context).setTitle("Verify app lock")
            .setNegativeButton("Cancel", context.mainExecutor) { _, _ -> cancel() }
        if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        try {
            builder.build().authenticate(signal, context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (signal.isCanceled || activeSignal.value !== signal) return
                    activeSignal.value = null
                    verified()
                }
                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (signal.isCanceled || activeSignal.value !== signal) return
                    activeSignal.value = null
                    if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                        error = message.toString()
                    }
                }
            })
        } catch (_: RuntimeException) {
            cancel()
            error = "Fingerprint verification unavailable."
        }
    }) { Text(label) }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("Biometric verification unavailable") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
    }
}
