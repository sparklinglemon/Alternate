package com.lulu786.Alternate

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.settings() = getSharedPreferences("$packageName.settings", Context.MODE_PRIVATE)
fun Context.dp(v: Number) = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
fun alpha(color: Int, a: Int) = (color and 0xFFFFFF) or (a shl 24)

class Palette(
    val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
    val secondaryContainer: Int, val onSecondaryContainer: Int, val surface: Int, val onSurface: Int,
    val onSurfaceVariant: Int, val container: Int, val containerHigh: Int, val outline: Int,
    val error: Int, val onError: Int, val dark: Boolean,
)

object Theme {
    val modes = linkedMapOf("system" to "System default", "light" to "Light", "dark" to "Dark")

    fun mode(ctx: Context) = ctx.settings().getString("theme", "system") ?: "system"

    fun isDark(ctx: Context) = when (mode(ctx)) {
        "dark" -> true
        "light" -> false
        else -> ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    /** Material You colours from the wallpaper on Android 12+, the Material 3 baseline otherwise. */
    fun palette(ctx: Context): Palette {
        val dark = isDark(ctx)
        if (Build.VERSION.SDK_INT >= 31) {
            fun c(id: Int) = ctx.resources.getColor(id, null)
            return if (dark) Palette(
                c(android.R.color.system_accent1_200), c(android.R.color.system_accent1_800),
                c(android.R.color.system_accent1_700), c(android.R.color.system_accent1_100),
                c(android.R.color.system_accent2_700), c(android.R.color.system_accent2_100),
                c(android.R.color.system_neutral1_900), c(android.R.color.system_neutral1_100),
                c(android.R.color.system_neutral2_200), c(android.R.color.system_neutral1_800),
                c(android.R.color.system_neutral1_700), c(android.R.color.system_neutral2_400),
                0xFFF2B8B5.toInt(), 0xFF601410.toInt(), true,
            ) else Palette(
                c(android.R.color.system_accent1_600), c(android.R.color.system_accent1_0),
                c(android.R.color.system_accent1_100), c(android.R.color.system_accent1_900),
                c(android.R.color.system_accent2_100), c(android.R.color.system_accent2_900),
                c(android.R.color.system_neutral1_10), c(android.R.color.system_neutral1_900),
                c(android.R.color.system_neutral2_700), c(android.R.color.system_neutral1_50),
                c(android.R.color.system_neutral1_100), c(android.R.color.system_neutral2_500),
                0xFFB3261E.toInt(), Color.WHITE, false,
            )
        }
        return if (dark) Palette(
            0xFFD0BCFF.toInt(), 0xFF381E72.toInt(), 0xFF4F378B.toInt(), 0xFFEADDFF.toInt(), 0xFF4A4458.toInt(),
            0xFFE8DEF8.toInt(), 0xFF141218.toInt(), 0xFFE6E0E9.toInt(), 0xFFCAC4D0.toInt(), 0xFF2B2930.toInt(),
            0xFF36343B.toInt(), 0xFF938F99.toInt(), 0xFFF2B8B5.toInt(), 0xFF601410.toInt(), true,
        ) else Palette(
            0xFF6750A4.toInt(), Color.WHITE, 0xFFEADDFF.toInt(), 0xFF21005D.toInt(), 0xFFE8DEF8.toInt(),
            0xFF1D192B.toInt(), 0xFFFEF7FF.toInt(), 0xFF1D1B20.toInt(), 0xFF49454F.toInt(), 0xFFF3EDF7.toInt(),
            0xFFE6E0E9.toInt(), 0xFF79747E.toInt(), 0xFFB3261E.toInt(), Color.WHITE, false,
        )
    }

    // Letter avatar colours (background, text) for light and dark, A-Z.
    private const val LIGHT =
        "646464f0f0f0,0d74cee6f4fe,107d98def7f9,ff92ad381525,208368e6f7ed,cc4e00ffefd6,218358e6f6eb,60655feff1ef," +
            "5753c6f0f1fe,208368e6f7ed,107d98def7f9,5c7c2feef6d6,027864ddf9f2,3a5bc7edf2fe,cc4e00ffefd6,c2298afee9f5," +
            "8145b5f7edfe,ce2c31feebec,00749ee1f6fd,008573e0f8f3,8145b5f7edfe,6550b9f4f0fe,9e6c00fffab8,953ea3fbebfb," +
            "9e6c00fffab8,7d5e54f6edea"
    private const val DARK =
        "b4b4b4222222,70b8ff0d2847,4ccce6082c36,ff92ad381525,1fd8a40f2e22,ffa057331e0b,3dd68c132d21,afb5ad212220," +
            "b1a9ff202248,1fd8a40f2e22,4ccce6082c36,bde56c1f2917,58d5ba092c2b,9eb1ff182449,ffa057331e0b,ff8dcc37172f," +
            "d19dff301c3b,ff95923b1219,75c7f0112840,0bd8b60d2d2a,d19dff301c3b,baa7ff291f43,f5e1472d2305,e796f3351a35," +
            "f5e1472d2305,d4b3a5262220"

    fun avatarColors(letter: String, index: Int, dark: Boolean): Pair<Int, Int> {
        var h = 0
        for (ch in "${letter.lowercase()}_$index") h = (h shl 5) - h + ch.code
        val i = ((Math.abs(h.toLong()) * 31) % 26).toInt()
        val e = (if (dark) DARK else LIGHT).split(',')[i]
        return (0xFF000000 or e.substring(0, 6).toLong(16)).toInt() to (0xFF000000 or e.substring(6).toLong(16)).toInt()
    }
}

object Photos {
    private val cache = LruCache<String, Bitmap>(64)

    fun decode(photo: String): Bitmap? {
        if (photo.isEmpty()) return null
        val key = photo.length.toString() + photo.hashCode()
        cache.get(key)?.let { return it }
        return runCatching {
            val bytes = Base64.decode(photo.substringAfter(','), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.also { cache.put(key, it) }
    }

    /** Square-crops, scales to [size] px and returns a JPEG data URI (what the database and vCards store). */
    fun encode(src: Bitmap, size: Int = 400): String {
        val side = minOf(src.width, src.height)
        val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = if (side > size) Bitmap.createScaledBitmap(crop, size, size, true) else crop
        val bytes = java.io.ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 75, bytes)
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }
}

/** Circular avatar: photo, letter, or an icon. */
class Avatar(ctx: Context) : View(ctx) {
    var bg = Color.GRAY
    var fg = Color.WHITE
    var letter = ""
    var bitmap: Bitmap? = null
    var icon: Drawable? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun show(contact: Contact, index: Int, dark: Boolean) {
        val (b, f) = Theme.avatarColors(contact.letter, index, dark)
        bg = b; fg = f; letter = contact.letter; icon = null
        bitmap = Photos.decode(contact.photo)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val r = w / 2
        val bmp = bitmap
        paint.shader = null
        if (bmp != null) {
            val m = Matrix()
            val scale = w / minOf(bmp.width, bmp.height)
            m.setScale(scale, scale)
            m.postTranslate((w - bmp.width * scale) / 2, (w - bmp.height * scale) / 2)
            paint.shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            canvas.drawCircle(r, r, r, paint)
            return
        }
        paint.color = bg
        canvas.drawCircle(r, r, r, paint)
        icon?.let {
            val s = (w * 0.45f).toInt()
            val o = (width - s) / 2
            it.setBounds(o, o, o + s, o + s)
            it.setTint(fg)
            it.draw(canvas)
            return
        }
        paint.color = fg
        paint.textSize = w * 0.45f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(letter, r, r - (paint.descent() + paint.ascent()) / 2, paint)
    }
}

enum class Btn { FILLED, TONAL, OUTLINED, DANGER }

abstract class BaseActivity : Activity() {
    lateinit var p: Palette
    private var dark = false

    override fun onCreate(savedInstanceState: Bundle?) {
        dark = Theme.isDark(this)
        setTheme(if (dark) android.R.style.Theme_DeviceDefault_NoActionBar else android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        p = Theme.palette(this)
        window.setBackgroundDrawable(ColorDrawable(p.surface))
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = p.surface
            window.navigationBarColor = p.surface
        }
    }

    override fun onResume() {
        super.onResume()
        if (dark != Theme.isDark(this)) {
            recreate()
            return
        }
        if (Lock.locked && this !is LockActivity) {
            startActivity(Intent(this, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
    }

    fun setScreen(content: View) {
        setContentView(FrameLayout(this).apply {
            fitsSystemWindows = true
            addView(content, MATCH, MATCH)
        })
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (dark) 0 else
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        }
    }

    // ---- small view builders ----

    val rippleColor get() = alpha(p.onSurface, 0x26)

    fun shape(color: Int, radius: Float = 0f, radii: FloatArray? = null, stroke: Int = 0, strokeColor: Int = 0, oval: Boolean = false) =
        GradientDrawable().apply {
            setColor(color)
            if (oval) this.shape = GradientDrawable.OVAL
            if (radii != null) cornerRadii = radii else cornerRadius = radius
            if (stroke > 0) setStroke(stroke, strokeColor)
        }

    fun ripple(content: Drawable?, mask: Drawable? = content, color: Int = rippleColor) =
        RippleDrawable(ColorStateList.valueOf(color), content, mask)

    fun vertical(vararg children: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        children.forEach { addView(it) }
    }

    fun horizontal(vararg children: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        children.forEach { addView(it) }
    }

    fun scroll(child: View) = ScrollView(this).apply {
        isFillViewport = true
        addView(child)
    }

    fun text(s: CharSequence, size: Float = 16f, color: Int = p.onSurface, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun drawable(res: Int, color: Int, size: Int = 24): Drawable = getDrawable(res)!!.mutate().apply {
        setTint(color)
        setBounds(0, 0, dp(size), dp(size))
    }

    fun icon(res: Int, color: Int = p.onSurfaceVariant, size: Int = 24) = ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    fun iconButton(res: Int, desc: String, color: Int = p.onSurfaceVariant, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color)
        contentDescription = desc
        scaleType = ImageView.ScaleType.CENTER
        background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, null)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener { onClick() }
    }

    fun button(label: String, style: Btn, iconRes: Int = 0, onClick: () -> Unit) = TextView(this).apply {
        val (bg, fg) = when (style) {
            Btn.FILLED -> p.primary to p.onPrimary
            Btn.TONAL -> p.secondaryContainer to p.onSecondaryContainer
            Btn.OUTLINED -> Color.TRANSPARENT to p.primary
            Btn.DANGER -> p.error to p.onError
        }
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minHeight = dp(52)
        setPadding(dp(20), 0, dp(20), 0)
        setTextColor(fg)
        val r = dp(100).toFloat()
        background = ripple(
            shape(bg, r, stroke = if (style == Btn.OUTLINED) dp(1) else 0, strokeColor = p.outline),
            shape(Color.BLACK, r), alpha(fg, 0x33),
        )
        if (iconRes != 0) {
            setCompoundDrawablesRelative(drawable(iconRes, fg, 18), null, null, null)
            compoundDrawablePadding = dp(8)
        }
        setOnClickListener { onClick() }
    }

    /** Rounded list group: big corners at the ends, small ones in between, 2dp gaps (Material 3 style). */
    fun group(title: String?, rows: List<View>) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val all = (if (title != null) listOf(text(title, 16f, p.onSurface, true).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }) else emptyList()) + rows
        all.forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { if (i > 0) topMargin = dp(2) }) }
        regroup(this)
    }

    /** (Re)applies the group corners to the currently visible rows. */
    fun regroup(g: LinearLayout) {
        val shown = (0 until g.childCount).map { g.getChildAt(it) }.filter { it.visibility != View.GONE }
        val big = dp(16).toFloat()
        val small = dp(5).toFloat()
        shown.forEachIndexed { i, v ->
            val top = if (i == 0) big else small
            val bottom = if (i == shown.lastIndex) big else small
            val radii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
            v.background = ripple(shape(p.container, radii = radii), shape(Color.BLACK, radii = radii))
        }
    }

    fun row(
        iconRes: Int, title: CharSequence, subtitle: CharSequence? = null, titleColor: Int = p.onSurface,
        trailing: View? = null, iconView: View? = null, onLong: (() -> Unit)? = null, onClick: (() -> Unit)? = null,
    ) = horizontal().apply {
        minimumHeight = dp(56)
        setPadding(dp(16), dp(12), dp(12), dp(12))
        val ic = iconView ?: if (iconRes != 0) icon(iconRes) else null
        ic?.let { addView(it, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) }) }
        addView(vertical(text(title, 16f, titleColor)).apply {
            subtitle?.let { addView(text(it, 13f, p.onSurfaceVariant)) }
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        trailing?.let { addView(it) }
        onClick?.let { f -> setOnClickListener { f() } }
        onLong?.let { f -> setOnLongClickListener { f(); true } }
    }

    fun lp(w: Int = MATCH, h: Int = WRAP, top: Int = 0, bottom: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    fun copy(text: String, msg: String = "Copied to clipboard") {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Alternate", text))
        toast(msg)
    }

    fun alert(title: String, msg: String? = null, ok: String = "OK", cancel: String? = null, onOk: () -> Unit = {}) =
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton(ok) { _, _ -> onOk() }
            .apply { if (cancel != null) setNegativeButton(cancel, null) }.show()

    fun open(uri: String) = runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess.also { if (!it) toast("No app found to open this") }
}

/** Top app bar: optional navigation icon, title, action icons. */
class Bar(private val a: BaseActivity, large: Boolean = false) : LinearLayout(a) {
    val nav = a.iconButton(R.drawable.ic_back, "Back", a.p.onSurface) { @Suppress("DEPRECATION") a.onBackPressed() }
    val title = a.text("", if (large) 28f else 22f, a.p.onSurface)
    private val actions = LinearLayout(a)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = a.dp(if (large) 72 else 64)
        setPadding(a.dp(4), 0, a.dp(4), 0)
        addView(nav)
        addView(title, LayoutParams(0, WRAP, 1f).apply { marginStart = a.dp(12) })
        addView(actions)
    }

    fun action(res: Int, desc: String, onClick: () -> Unit): ImageView =
        a.iconButton(res, desc, a.p.onSurfaceVariant, onClick).also { actions.addView(it) }

    fun clearActions() = actions.removeAllViews()
}

/** Outlined text field with a leading icon, a label shown above once filled, and an error line. */
class Field(private val a: BaseActivity, private val label: String, iconRes: Int, multiline: Boolean = false) {
    private val caption = a.text(label, 12f, a.p.onSurfaceVariant)
    val edit = EditText(a).apply {
        background = null
        hint = label
        textSize = 16f
        setTextColor(a.p.onSurface)
        setHintTextColor(a.p.onSurfaceVariant)
        setPadding(0, a.dp(4), 0, a.dp(4))
        if (Build.VERSION.SDK_INT >= 29) textCursorDrawable = a.shape(a.p.primary).apply { setSize(a.dp(2), 0) }
        if (!multiline) isSingleLine = true else minLines = 2
    }
    val leading = a.icon(iconRes)
    val box = a.horizontal(leading, a.vertical(caption, edit)).apply {
        setPadding(a.dp(12), a.dp(8), a.dp(4), a.dp(8))
        minimumHeight = a.dp(56)
        (getChildAt(1).layoutParams as LinearLayout.LayoutParams).apply { width = 0; weight = 1f; marginStart = a.dp(12) }
    }
    private val err = a.text("", 12f, a.p.error).apply {
        visibility = View.GONE
        setPadding(a.dp(16), a.dp(4), 0, 0)
    }
    val view = a.vertical(box, err)
    private var error = false

    var value: String
        get() = edit.text.toString()
        set(v) = edit.setText(v)

    init {
        leading.layoutParams = LinearLayout.LayoutParams(a.dp(24), a.dp(24))
        edit.setOnFocusChangeListener { _, _ -> refresh() }
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (error) setError(null)
                refresh()
            }
        })
        refresh()
    }

    fun trailing(res: Int, desc: String, onClick: () -> Unit) = box.addView(a.iconButton(res, desc) { onClick() })

    fun setError(msg: String?) {
        error = msg != null
        err.text = msg ?: ""
        err.visibility = if (msg == null) View.GONE else View.VISIBLE
        refresh()
    }

    private fun refresh() {
        val focused = edit.hasFocus()
        caption.visibility = if (edit.text.isNotEmpty()) View.VISIBLE else View.GONE
        caption.setTextColor(if (error) a.p.error else if (focused) a.p.primary else a.p.onSurfaceVariant)
        box.background = a.shape(
            Color.TRANSPARENT, a.dp(12).toFloat(), stroke = a.dp(if (focused || error) 2 else 1),
            strokeColor = if (error) a.p.error else if (focused) a.p.primary else a.p.outline,
        )
    }
}

fun View.enable(on: Boolean) {
    isEnabled = on
    alpha = if (on) 1f else 0.38f
}

fun Activity.hideKeyboard() {
    currentFocus?.let {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(it.windowToken, 0)
    }
}
