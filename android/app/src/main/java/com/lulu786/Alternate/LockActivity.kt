package com.lulu786.Alternate

import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen PIN pad shown whenever the app is locked. */
class LockActivity : BaseActivity() {
    private var pin = ""
    private val dots = ArrayList<View>()
    private lateinit var error: TextView
    private var prompted = false

    private val useBiometric get() = Lock.biometric(this) && Lock.biometricAvailable(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Lock.locked) {
            finish()
            return
        }
        error = text("", 14f, p.error).apply { gravity = Gravity.CENTER }
        val dotRow = horizontal().apply { gravity = Gravity.CENTER }
        repeat(4) {
            val d = View(this)
            dots += d
            dotRow.addView(d, LinearLayout.LayoutParams(dp(14), dp(14)).apply { setMargins(dp(10), 0, dp(10), 0) })
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "del", "0", if (useBiometric) "bio" else "")
        val pad = vertical().apply { gravity = Gravity.CENTER_HORIZONTAL }
        keys.chunked(3).forEach { line ->
            pad.addView(horizontal().apply {
                line.forEach { addView(key(it), LinearLayout.LayoutParams(dp(76), dp(76)).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) }) }
            })
        }
        setScreen(vertical(
            icon(R.drawable.ic_lock, p.primary, 48),
            text("App Locked", 26f).apply { gravity = Gravity.CENTER },
            text("Enter your PIN to unlock", 15f, p.onSurfaceVariant).apply { gravity = Gravity.CENTER },
            dotRow, error, pad,
        ).apply {
            gravity = Gravity.CENTER
            getChildAt(2).layoutParams = lp(top = 8)
            dotRow.layoutParams = lp(top = 32)
            error.layoutParams = lp(top = 16, bottom = 16).apply { height = dp(20) }
        })
        updateDots()
    }

    override fun onResume() {
        super.onResume()
        if (!prompted && useBiometric) {
            prompted = true
            window.decorView.postDelayed({ biometric() }, 400)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun key(k: String): View {
        val bg = shape(p.containerHigh, oval = true)
        val v: View = when (k) {
            "" -> View(this)
            "del" -> icon(R.drawable.ic_backspace, p.onSurface).apply { contentDescription = "Delete" }
            "bio" -> icon(R.drawable.ic_fingerprint, p.onSurface).apply { contentDescription = "Use biometrics" }
            else -> text(k, 28f).apply { gravity = Gravity.CENTER }
        }
        if (k.isEmpty()) return v
        (v as? android.widget.ImageView)?.scaleType = android.widget.ImageView.ScaleType.CENTER
        v.background = ripple(if (k.length == 1) bg else null, bg)
        v.setOnClickListener {
            when (k) {
                "del" -> pin = pin.dropLast(1)
                "bio" -> biometric()
                else -> if (pin.length < 4) pin += k
            }
            error.text = ""
            updateDots()
            if (pin.length == 4) submit()
        }
        return v
    }

    private fun updateDots() = dots.forEachIndexed { i, d ->
        d.background = if (i < pin.length) shape(p.primary, oval = true) else shape(0, oval = true, stroke = dp(2), strokeColor = p.outline)
    }

    private fun submit() {
        when (Lock.verify(this, pin)) {
            Lock.Result.OK -> return unlock()
            Lock.Result.WRONG -> error.text = "Invalid PIN. Please try again."
            Lock.Result.LOCKED_OUT -> error.text = "Too many failed attempts. Please try again later."
        }
        pin = ""
        updateDots()
    }

    private fun unlock() {
        Lock.unlocked(this)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun biometric() {
        if (Build.VERSION.SDK_INT < 28 || isFinishing) return
        BiometricPrompt.Builder(this)
            .setTitle("Unlock Alternate")
            .setNegativeButton("Use PIN", mainExecutor) { _, _ -> }
            .build()
            .authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = unlock()

                override fun onAuthenticationError(code: Int, msg: CharSequence?) {
                    if (code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED && code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                        error.text = msg ?: "Biometric error. Please use PIN instead."
                    }
                }
            })
    }
}
