package com.lulu786.Alternate

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.Calendar

class EditActivity : BaseActivity() {
    private val extras = linkedMapOf(
        "prefix" to ("Prefix" to R.drawable.ic_prefix),
        "suffix" to ("Suffix" to R.drawable.ic_suffix),
        "email" to ("Email" to R.drawable.ic_email_outline),
        "notes" to ("Notes" to R.drawable.ic_notes),
        "website" to ("Website" to R.drawable.ic_link),
        "birthday" to ("Birthday" to R.drawable.ic_cake),
        "nickname" to ("Nickname" to R.drawable.ic_nickname),
    )
    private var original: Contact? = null
    private lateinit var country: Country
    private var photo = ""
    private var birthday = ""
    private val visible = LinkedHashSet<String>()
    private val fields = HashMap<String, Field>()

    private lateinit var avatar: Avatar
    private lateinit var name: Field
    private lateinit var number: Field
    private lateinit var countryButton: TextView
    private lateinit var appointment: Field
    private lateinit var location: Field
    private lateinit var extraBox: LinearLayout
    private lateinit var addButton: TextView
    private lateinit var saveButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val o = Store.byNumber(intent.getStringExtra("number"))
        if (intent.hasExtra("number") && o == null) {
            finish()
            return
        }
        original = o
        country = Phone.byIso(o?.countryCode) ?: Phone.byIso(Phone.dialCountry(this)) ?: Phone.countries.first()
        photo = o?.photo ?: ""
        birthday = o?.birthday ?: ""

        val bar = Bar(this).apply { title.text = if (o == null) "Add Contact" else "Edit Contact" }

        avatar = Avatar(this).apply {
            contentDescription = "Contact photo"
            setOnClickListener { pickPhoto() }
        }
        name = Field(this, "Name *", R.drawable.ic_person).apply {
            value = o?.name ?: ""
            edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        number = Field(this, "Phone Number *", R.drawable.ic_phone_outline).apply {
            edit.inputType = InputType.TYPE_CLASS_PHONE
            leading.visibility = View.GONE
            value = o?.let { nationalOf(it) } ?: ""
            edit.addTextChangedListener(PasteFixer())
        }
        countryButton = text("", 15f, p.onSurface).apply {
            gravity = Gravity.CENTER
            minHeight = dp(56)
            setPadding(dp(12), 0, dp(12), 0)
            val bg = shape(0, dp(12).toFloat(), stroke = dp(1), strokeColor = p.outline)
            background = ripple(bg, bg)
            setOnClickListener { pickCountry() }
        }
        appointment = Field(this, "Appointment", R.drawable.ic_work).apply { value = o?.appointment ?: "" }
        location = Field(this, "Location", R.drawable.ic_location).apply { value = o?.location ?: "" }
        extraBox = vertical()
        addButton = button("Add fields", Btn.TONAL) { addField() }
        saveButton = button("Save Contact", Btn.FILLED) { save() }

        if (o != null) {
            val existing = mapOf(
                "prefix" to o.prefix, "suffix" to o.suffix, "email" to o.email, "notes" to o.notes,
                "website" to o.website, "birthday" to o.birthday, "nickname" to o.nickname,
            )
            extras.keys.filter { existing[it]!!.isNotEmpty() }.forEach { key ->
                visible += key
                if (key != "birthday") field(key).value = existing[key]!!
            }
        }

        val form = vertical().apply {
            setPadding(dp(24), dp(8), dp(24), dp(32))
            addView(avatar, LinearLayout.LayoutParams(dp(160), dp(160)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8)
            })
            addView(name.view, lp(top = 16))
            addView(horizontal(countryButton, number.view).apply {
                gravity = Gravity.TOP
                (getChildAt(1).layoutParams as LinearLayout.LayoutParams).apply { width = 0; weight = 1f; marginStart = dp(12) }
            }, lp(top = 16))
            addView(appointment.view, lp(top = 16))
            addView(location.view, lp(top = 16))
            addView(extraBox, lp())
            addView(addButton, lp(top = 36))
            addView(saveButton, lp(top = 12))
        }
        setScreen(vertical(bar, scroll(form)).apply { getChildAt(1).layoutParams = lp(h = 0, weight = 1f) })
        updateCountry()
        updatePhoto()
        renderExtras()
    }

    /**
     * Numbers pasted from a dialer look like "+91 98765 43210", "(555) 123-4567" or carry invisible direction
     * marks, which used to fail the digits-only check. Clean them up as they arrive and switch the country when
     * the number starts with an international prefix.
     */
    private inner class PasteFixer : TextWatcher {
        private var pasted = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            pasted = count > 1
        }

        override fun afterTextChanged(s: Editable) {
            val t = s.toString()
            val typingPlus = !pasted && t.matches(Regex("\\+\\d*"))
            if (typingPlus || (!pasted && t.all { it in '0'..'9' })) return
            pasted = false
            val parsed = Phone.parse(t, country)
            if (parsed == null) {
                if (Phone.clean(t).isEmpty() && t.isNotEmpty()) s.clear()
                return
            }
            if (parsed.country !== country) {
                country = parsed.country
                updateCountry()
            }
            if (parsed.national != t) s.replace(0, s.length, parsed.national)
        }
    }

    /** National part of a stored number (very old entries kept the dial code in phoneNumber too). */
    private fun nationalOf(c: Contact): String {
        val dial = Phone.byIso(c.countryCode)?.dial ?: return c.phoneNumber
        return when {
            c.fullPhoneNumber == dial + c.phoneNumber -> c.phoneNumber
            c.fullPhoneNumber.startsWith(dial) -> c.fullPhoneNumber.substring(dial.length)
            else -> c.phoneNumber
        }
    }

    private fun updateCountry() {
        countryButton.text = "${country.flag} ▾ +${country.dial}"
        countryButton.contentDescription = "Selected country: ${country.name}, dial code +${country.dial}"
    }

    private fun updatePhoto() {
        val bmp = Photos.decode(photo)
        avatar.bitmap = bmp
        avatar.bg = p.primaryContainer
        avatar.fg = p.onPrimaryContainer
        avatar.icon = if (bmp == null) getDrawable(R.drawable.ic_add_photo) else null
        avatar.invalidate()
    }

    private fun field(key: String): Field = fields.getOrPut(key) {
        val (label, res) = extras[key]!!
        Field(this, label, res, multiline = key == "notes").apply {
            when (key) {
                "email" -> edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                "website" -> edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                "notes" -> edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                "birthday" -> {
                    edit.isFocusable = false
                    edit.isCursorVisible = false
                    edit.setOnClickListener { pickBirthday() }
                    box.setOnClickListener { pickBirthday() }
                }
                else -> edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            trailing(R.drawable.ic_close, "Remove $label") {
                visible -= key
                value = ""
                if (key == "birthday") birthday = ""
                renderExtras()
            }
        }
    }

    private fun renderExtras() {
        extraBox.removeAllViews()
        extras.keys.filter { it in visible }.forEach { key ->
            val f = field(key)
            if (key == "birthday") f.value = if (birthday.isEmpty()) "" else formatBirthday(birthday)
            (f.view.parent as? LinearLayout)?.removeView(f.view)
            extraBox.addView(f.view, lp(top = 16))
        }
        addButton.enable(visible.size < extras.size)
    }

    private fun addField() {
        val remaining = extras.keys.filter { it !in visible }
        if (remaining.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Choose fields to add")
            .setItems(remaining.map { extras[it]!!.first }.toTypedArray()) { _, i ->
                val key = remaining[i]
                visible += key
                renderExtras()
                if (key == "birthday") pickBirthday() else field(key).edit.requestFocus()
            }
            .show()
    }

    private fun pickBirthday() {
        val cal = Calendar.getInstance()
        Regex("(\\d{4}|-)-(\\d{2})-(\\d{2})").matchEntire(birthday)?.let {
            val (y, m, d) = it.destructured
            if (y != "-") cal.set(Calendar.YEAR, y.toInt())
            cal.set(Calendar.MONTH, m.toInt() - 1)
            cal.set(Calendar.DAY_OF_MONTH, d.toInt())
        }
        DatePickerDialog(this, { _, y, m, d ->
            birthday = "%04d-%02d-%02d".format(y, m + 1, d)
            renderExtras()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickCountry() {
        val all = Phone.countries.sortedBy { it.name }
        var shown = all
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, parent: android.view.ViewGroup): View {
                val c = shown[i]
                val tv = v as? TextView ?: text("", 16f).apply {
                    setPadding(dp(24), dp(14), dp(24), dp(14))
                }
                tv.text = "${c.flag}   ${c.name}  (+${c.dial})"
                tv.setTextColor(if (c === country) p.primary else p.onSurface)
                return tv
            }
        }
        val searchBox = EditText(this).apply {
            hint = "Search country or code"
            isSingleLine = true
            textSize = 16f
        }
        val list = ListView(this).apply {
            this.adapter = adapter
            divider = null
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select country")
            .setView(vertical(searchBox, list).apply {
                setPadding(dp(20), dp(8), dp(20), 0)
                list.layoutParams = lp(h = dp(420))
            })
            .setNegativeButton("Cancel", null)
            .create()
        list.setOnItemClickListener { _, _, i, _ ->
            country = shown[i]
            updateCountry()
            dialog.dismiss()
        }
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim().removePrefix("+").lowercase()
                shown = if (q.isEmpty()) all else all.filter {
                    it.name.lowercase().contains(q) || it.dial.startsWith(q) || it.iso.lowercase() == q
                }
                adapter.notifyDataSetChanged()
            }
        })
        dialog.show()
    }

    private fun pickPhoto() {
        val options = mutableListOf("Take photo", "Choose from gallery")
        if (photo.isNotEmpty()) options += "Remove photo"
        AlertDialog.Builder(this)
            .setTitle("Contact photo")
            .setItems(options.toTypedArray()) { _, i ->
                runCatching {
                    when (i) {
                        0 -> startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQ_CAMERA)
                        1 -> startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_GALLERY)
                        else -> {
                            photo = ""
                            updatePhoto()
                        }
                    }
                }.onFailure { toast("No app found to pick a photo") }
            }
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        Store.bg({
            when (requestCode) {
                REQ_CAMERA -> (data.extras?.get("data") as? Bitmap)?.let { Photos.encode(it) }
                REQ_GALLERY -> data.data?.let { load(it) }?.let { Photos.encode(it) }
                else -> null
            }
        }) { r ->
            val uri = r.getOrNull()
            if (uri == null) toast("Failed to process the image. Please try again.")
            else {
                photo = uri
                updatePhoto()
            }
        }
    }

    private fun load(uri: Uri): Bitmap? {
        val cr = contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 400) sample *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val degrees = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (degrees == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun value(key: String) = if (key in visible) fields[key]?.value?.trim() ?: "" else ""

    private fun save() {
        hideKeyboard()
        var ok = true
        val n = name.value.trim()
        if (n.length < 2) {
            name.setError(if (n.isEmpty()) "Name is required" else "Name must be at least 2 characters")
            ok = false
        }
        val raw = number.value
        val parsed = Phone.parse(raw, country)
        if (parsed == null) {
            number.setError(if (raw.isBlank()) "Phone number is required" else "Enter a valid phone number")
            ok = false
        } else {
            if (parsed.country !== country) {
                country = parsed.country
                updateCountry()
            }
            if (parsed.national != raw) number.value = parsed.national
            val dup = Store.byNumber(parsed.full)
            if (dup != null && dup.fullPhoneNumber != original?.fullPhoneNumber) {
                number.setError("This number already exists")
                ok = false
            }
        }
        if (!ok || parsed == null) return

        val o = original
        val contact = Contact(
            fullPhoneNumber = parsed.full,
            phoneNumber = parsed.national,
            countryCode = parsed.country.iso,
            name = n,
            appointment = appointment.value.trim(),
            location = location.value.trim(),
            iosRow = o?.iosRow ?: "",
            suffix = value("suffix"),
            prefix = value("prefix"),
            email = value("email"),
            notes = value("notes"),
            website = value("website"),
            birthday = if ("birthday" in visible) birthday else "",
            labels = o?.labels ?: "",
            nickname = value("nickname"),
            photo = photo,
        )
        saveButton.enable(false)
        Store.save(this, contact, o?.fullPhoneNumber) { success ->
            if (success) {
                if (o != null) {
                    startActivity(Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
                finish()
            } else {
                saveButton.enable(true)
                toast("Failed to save contact. Please try again.")
            }
        }
    }

    companion object {
        private const val REQ_CAMERA = 1
        private const val REQ_GALLERY = 2
    }
}
