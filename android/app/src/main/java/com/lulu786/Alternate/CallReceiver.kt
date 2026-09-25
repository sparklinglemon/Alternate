package com.lulu786.Alternate

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Shows a floating caller card for saved numbers when a call rings or is placed. */
class CallReceiver : BroadcastReceiver() {
    companion object {
        /** Number reported by [CallDetectScreeningService] when the broadcast doesn't carry one. */
        @Volatile
        var callServiceNumber: String? = null
        private var showing = false
        private var overlay: View? = null
        private var collapsed = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!Settings.canDrawOverlays(context)) return
        val prefs = context.settings()
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> show(context, intent, prefs.getBoolean("show_incoming_popup", true))
            TelephonyManager.EXTRA_STATE_OFFHOOK -> show(context, intent, prefs.getBoolean("show_outgoing_popup", true))
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (!showing) return
                showing = false
                callServiceNumber = null
                dismiss(context)
            }
        }
    }

    private fun show(context: Context, intent: Intent, enabled: Boolean) {
        if (showing || !enabled) return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: callServiceNumber ?: return
        showing = true
        val contact = runCatching { Db.get(context).find(Phone.lookupKeys(context, number)) }
            .onFailure { Log.e("CallReceiver", "lookup failed", it) }.getOrNull() ?: return
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { attach(app, contact) } }, 500)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach(ctx: Context, c: Contact) {
        if (!showing) return
        dismissNow(ctx)
        val p = Theme.palette(ctx)
        fun dp(v: Int) = ctx.dp(v)
        fun tv(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        fun iv(res: Int, color: Int, size: Int) = ImageView(ctx).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(color)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
        fun btn(res: Int, desc: String, onClick: () -> Unit) = iv(res, p.onSurfaceVariant, 40).apply {
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = desc
            background = RippleDrawable(ColorStateList.valueOf(alpha(p.onSurface, 0x26)), null, null)
            setOnClickListener { onClick() }
        }
        fun info(res: Int, s: String) = tv(s, 14f, p.onSecondaryContainer).apply {
            setCompoundDrawablesRelative(ctx.getDrawable(res)!!.mutate().apply {
                setTint(p.onSecondaryContainer)
                setBounds(0, 0, dp(18), dp(18))
            }, null, null, null)
            compoundDrawablePadding = dp(6)
            setPadding(0, dp(4), 0, 0)
        }

        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val r = dp(12).toFloat()
        lateinit var root: LinearLayout
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            @Suppress("DEPRECATION")
            (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED),
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }

        val header = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            addView(ImageView(ctx).apply {
                setImageDrawable(runCatching { ctx.packageManager.getApplicationIcon(ctx.packageName) }.getOrNull())
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) }
            })
            addView(tv("Alternate", 16f, p.onSurface, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(btn(R.drawable.ic_minimize, "Minimize") { toggleCollapse(wm, root, params) })
            addView(btn(R.drawable.ic_close, "Close") {
                showing = false
                dismiss(ctx)
            })
        }
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(p.secondaryContainer)
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            }
            val avatar = Avatar(ctx).apply { show(c, 0, p.dark) }
            addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(avatar, LinearLayout.LayoutParams(dp(45), dp(45)).apply { marginEnd = dp(12) })
                addView(tv(c.displayName, 18f, p.onSecondaryContainer, true), LinearLayout.LayoutParams(0, -2, 1f))
            })
            if (c.appointment.isNotEmpty()) addView(info(R.drawable.ic_work, c.appointment), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            if (c.location.isNotEmpty()) addView(info(R.drawable.ic_location, c.location))
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(p.surface)
                cornerRadius = r
            }
            elevation = dp(6).toFloat()
            addView(header)
            addView(body)
        }
        root = LinearLayout(ctx).apply {
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(card, LinearLayout.LayoutParams(-1, -2))
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        root.setOnTouchListener { _, e ->
            if (collapsed) return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    runCatching { wm.updateViewLayout(root, params) }
                    true
                }
                else -> false
            }
        }
        root.setOnClickListener { if (collapsed) toggleCollapse(wm, root, params) }
        wm.addView(root, params)
        overlay = root
    }

    /** Parks the card at the right screen edge (mostly off-screen) or brings it back. */
    private fun toggleCollapse(wm: WindowManager, root: View, params: WindowManager.LayoutParams) {
        collapsed = !collapsed
        if (collapsed) {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = root.resources.displayMetrics.widthPixels - root.context.dp(60)
            params.gravity = Gravity.TOP or Gravity.START
            root.alpha = 0.8f
        } else {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.x = 0
            params.y = 0
            params.gravity = Gravity.CENTER
            root.alpha = 1f
        }
        runCatching { wm.updateViewLayout(root, params) }
    }

    private fun dismissNow(ctx: Context) {
        overlay?.let { v ->
            runCatching { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
        }
        overlay = null
        collapsed = false
    }

    private fun dismiss(ctx: Context) {
        val app = ctx.applicationContext
        Handler(Looper.getMainLooper()).post { dismissNow(app) }
    }
}

/** Captures the other party's number (Android 10+) when the phone broadcast doesn't include it. */
class CallDetectScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (Build.VERSION.SDK_INT >= 29 && (details.callDirection == Call.Details.DIRECTION_INCOMING ||
                details.callDirection == Call.Details.DIRECTION_OUTGOING)
        ) {
            CallReceiver.callServiceNumber = details.handle?.schemeSpecificPart
            respondToCall(details, CallResponse.Builder().build())
        }
    }
}
