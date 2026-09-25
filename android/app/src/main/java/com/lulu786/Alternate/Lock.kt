package com.lulu786.Alternate

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.os.Build
import java.security.MessageDigest
import java.util.UUID

/** App passcode lock: salted SHA-256 PIN, optional biometrics, auto-lock timeout. */
object Lock {
    val timeouts = linkedMapOf(
        "disabled" to "Disabled", "immediately" to "Immediately", "1m" to "1 minute",
        "5m" to "5 minutes", "1h" to "1 hour", "5h" to "5 hours",
    )
    private val timeoutMs = mapOf("1m" to 60_000L, "5m" to 300_000L, "1h" to 3_600_000L, "5h" to 18_000_000L)
    private const val MAX_ATTEMPTS = 5

    enum class Result { OK, WRONG, LOCKED_OUT }

    /** Whether the lock screen must be shown before any other screen. */
    @Volatile
    var locked = false

    private fun p(ctx: Context): SharedPreferences = ctx.getSharedPreferences("passcode", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = p(ctx).getBoolean("enabled", false)
    fun biometric(ctx: Context) = p(ctx).getBoolean("biometric", false)
    fun setBiometric(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("biometric", on).apply()
    fun timeout(ctx: Context) = p(ctx).getString("timeout", "5m") ?: "5m"
    fun setTimeout(ctx: Context, t: String) = p(ctx).edit().putString("timeout", t).apply()

    private fun hash(pin: String, salt: String) =
        MessageDigest.getInstance("SHA-256").digest((pin + salt).toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(ctx: Context, pin: String) {
        val salt = UUID.randomUUID().toString()
        p(ctx).edit().putBoolean("enabled", true).putString("hash", hash(pin, salt)).putString("salt", salt)
            .putBoolean("biometric", false).putInt("failed", 0).putLong("lockout", 0)
            .putLong("last", System.currentTimeMillis()).apply()
    }

    fun clear(ctx: Context) {
        p(ctx).edit().clear().apply()
        locked = false
    }

    fun lockedOut(ctx: Context) = System.currentTimeMillis() < p(ctx).getLong("lockout", 0)

    fun verify(ctx: Context, pin: String): Result {
        val prefs = p(ctx)
        if (lockedOut(ctx)) return Result.LOCKED_OUT
        val salt = prefs.getString("salt", null) ?: return Result.WRONG
        if (hash(pin, salt) == prefs.getString("hash", null)) {
            prefs.edit().putInt("failed", 0).putLong("lockout", 0).apply()
            return Result.OK
        }
        val failed = prefs.getInt("failed", 0) + 1
        val e = prefs.edit().putInt("failed", failed)
        if (failed >= MAX_ATTEMPTS) e.putInt("failed", 0).putLong("lockout", System.currentTimeMillis() + 60_000)
        e.apply()
        return Result.WRONG
    }

    fun onBackground(ctx: Context) {
        if (enabled(ctx)) p(ctx).edit().putLong("last", System.currentTimeMillis()).apply()
    }

    fun onForeground(ctx: Context) {
        if (shouldLock(ctx)) locked = true
    }

    private fun shouldLock(ctx: Context): Boolean {
        if (!enabled(ctx)) return false
        val prefs = p(ctx)
        if (prefs.getBoolean("force", false)) return true
        val last = prefs.getLong("last", 0)
        return when (val t = timeout(ctx)) {
            "immediately" -> last != 0L
            "disabled" -> false
            else -> last != 0L && System.currentTimeMillis() - last > (timeoutMs[t] ?: 300_000L)
        }
    }

    fun lockNow(ctx: Context) {
        p(ctx).edit().putBoolean("force", true).putLong("last", System.currentTimeMillis()).apply()
        locked = true
    }

    fun unlocked(ctx: Context) {
        p(ctx).edit().putBoolean("force", false).putLong("last", System.currentTimeMillis()).apply()
        locked = false
    }

    fun biometricAvailable(ctx: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 29 ->
            @Suppress("DEPRECATION")
            ctx.getSystemService(BiometricManager::class.java)?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        Build.VERSION.SDK_INT == 28 -> ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        else -> false
    }
}
