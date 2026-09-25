package com.lulu786.Alternate

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Locale

fun formatBirthday(value: String): String {
    val monthDay = value.startsWith("--")
    val date = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(if (monthDay) "2000-" + value.substring(2) else value)
    }.getOrNull() ?: return value
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), if (monthDay) "MMMMd" else "yMMMMd")
    return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
}

class ContactActivity : BaseActivity() {
    private var number: String? = null
    private val index get() = intent.getIntExtra("index", 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        number = intent.getStringExtra("number")
    }

    override fun onResume() {
        super.onResume()
        val c = Store.byNumber(number)
        if (c == null) {
            finish()
            return
        }
        render(c)
    }

    private fun render(c: Contact) {
        val bar = Bar(this)
        bar.action(R.drawable.ic_edit, "Edit") {
            startActivity(Intent(this, EditActivity::class.java).putExtra("number", c.fullPhoneNumber))
        }
        val phone = Phone.format(c)
        val avatar = Avatar(this).apply { show(c, index, p.dark) }
        val content = vertical().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), 0, dp(16), dp(32))
            addView(avatar, lp(dp(150), dp(150), top = 12, bottom = 20))
            addView(centered(text(c.displayName, 28f)).apply { setOnLongClickListener { copy(c.name); true } })
            if (c.nickname.isNotEmpty()) addView(centered(text(c.nickname, 16f, p.onSurfaceVariant)), lp(top = 4))
            if (c.appointment.isNotEmpty()) addView(centered(text(c.appointment, 16f, p.onSurfaceVariant)), lp(top = 4))

            addView(horizontal().apply {
                gravity = Gravity.CENTER
                addView(action(R.drawable.ic_phone, "Call") { open("tel:+${c.fullPhoneNumber}") })
                addView(action(R.drawable.ic_message, "Message") { open("sms:+${c.fullPhoneNumber}") })
                if (c.email.isNotEmpty()) addView(action(R.drawable.ic_email, "Email") { open("mailto:${c.email}") })
            }, lp(top = 24, bottom = 24))

            val info = mutableListOf<View>(
                row(R.drawable.ic_phone_outline, phone, "Mobile", onLong = { copy("+${c.fullPhoneNumber}") }),
            )
            if (c.email.isNotEmpty()) info += row(R.drawable.ic_email_outline, c.email, onLong = { copy(c.email) })
            if (c.location.isNotEmpty()) info += row(R.drawable.ic_location, c.location, onLong = { copy(c.location) })
            addView(group("Contact info", info), lp())

            addView(group("Connected Apps", apps(c, phone)), lp(top = 24))

            val about = mutableListOf<View>()
            if (c.website.isNotEmpty()) about += row(R.drawable.ic_link, c.website, titleColor = p.primary, onLong = { copy(c.website) }) {
                open(if (c.website.contains("://")) c.website else "https://${c.website}")
            }
            if (c.birthday.isNotEmpty()) {
                val b = formatBirthday(c.birthday)
                about += row(R.drawable.ic_cake, b, "Birthday", onLong = { copy(b) })
            }
            if (c.notes.isNotEmpty()) about += row(R.drawable.ic_notes, c.notes, onLong = { copy(c.notes) })
            if (about.isNotEmpty()) addView(group("About ${c.name.trim().substringBefore(' ')}", about), lp(top = 24))

            addView(button("Share Contact", Btn.TONAL) { Share.contacts(this@ContactActivity, listOf(c)) }, lp(top = 32))
            addView(button("Delete Contact", Btn.DANGER) {
                alert("Delete contact?", "This contact will be permanently deleted from your device", "Delete", "Cancel") {
                    Store.delete(this@ContactActivity, listOf(c.fullPhoneNumber)) { ok ->
                        if (ok) finish() else toast("Failed to delete contact")
                    }
                }
            }, lp(top = 12))
        }
        setScreen(vertical(bar, scroll(content)).apply { getChildAt(1).layoutParams = lp(h = 0, weight = 1f) })
    }

    private fun centered(v: android.widget.TextView) = v.apply { gravity = Gravity.CENTER }

    private fun action(res: Int, label: String, onClick: () -> Unit) = vertical().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        val pill = icon(res, p.onPrimary, 25).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER
            contentDescription = label
            val bg = shape(p.primary, dp(100).toFloat())
            background = ripple(bg, bg, alpha(p.onPrimary, 0x33))
            setOnClickListener { onClick() }
        }
        addView(pill, LinearLayout.LayoutParams(dp(85), dp(60)))
        addView(text(label, 16f, p.onSurface, true), lp(WRAP, top = 8))
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(12); marginEnd = dp(12) }
    }

    /** WhatsApp / Telegram rows that expand to message + call entries. */
    private fun apps(c: Contact, phone: String): List<View> {
        val n = c.fullPhoneNumber
        fun expandable(iconRes: Int, tint: Int, name: String, msg: () -> Unit, call: () -> Unit): List<View> {
            val chevron = icon(R.drawable.ic_expand)
            val children = listOf(
                row(R.drawable.ic_message_outline, "Message  $phone") { msg() },
                row(R.drawable.ic_phone_outline, "Voice call  $phone") { call() },
            ).onEach { it.visibility = View.GONE; it.setPadding(dp(56), dp(12), dp(12), dp(12)) }
            val head = row(0, name, iconView = icon(iconRes, tint), trailing = chevron) {
                val show = children[0].visibility != View.VISIBLE
                children.forEach { it.visibility = if (show) View.VISIBLE else View.GONE }
                chevron.setImageResource(if (show) R.drawable.ic_collapse else R.drawable.ic_expand)
                (children[0].parent as? LinearLayout)?.let { regroup(it) }
            }
            return listOf(head) + children
        }
        fun telegram(profile: Boolean) {
            if (!runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tg://resolve?phone=$n${if (profile) "&profile" else ""}")))
                }.isSuccess
            ) open("https://t.me/+$n${if (profile) "?profile" else ""}")
        }
        return expandable(R.drawable.ic_whatsapp, 0xFF25D366.toInt(), "WhatsApp", { open("https://wa.me/$n") }, { open("https://wa.me/$n") }) +
            expandable(R.drawable.ic_telegram, 0xFF26A5E4.toInt(), "Telegram", { telegram(false) }, { telegram(true) })
    }
}
