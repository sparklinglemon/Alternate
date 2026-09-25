package com.lulu786.Alternate

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val bar = Bar(this, large = true).apply { title.text = "Settings" }
        val prefs = settings()
        val lockOn = Lock.enabled(this)
        val content = vertical().apply {
            setPadding(dp(16), dp(8), dp(16), dp(32))

            addView(group("Caller ID", listOf(
                switchRow(R.drawable.ic_call_in, "Show Incoming Popup", "Show caller info when a call comes in",
                    prefs.getBoolean("show_incoming_popup", true)) { prefs.edit().putBoolean("show_incoming_popup", it).apply() },
                switchRow(R.drawable.ic_call_out, "Show Outgoing Popup", "Show caller info when you place a call",
                    prefs.getBoolean("show_outgoing_popup", true)) { prefs.edit().putBoolean("show_outgoing_popup", it).apply() },
            )))

            addView(group("Appearance", listOf(
                row(R.drawable.ic_theme, "Theme", Theme.modes[Theme.mode(this@SettingsActivity)]) {
                    choose("Theme", Theme.modes, Theme.mode(this@SettingsActivity)) {
                        prefs.edit().putString("theme", it).apply()
                        recreate()
                    }
                },
            )), lp(top = 24))

            val security = mutableListOf<View>(
                switchRow(R.drawable.ic_lock, "Passcode Lock", "Require a PIN to open the app", lockOn) { on ->
                    if (on) setupPin() else disablePin()
                },
            )
            if (lockOn && Lock.biometricAvailable(this@SettingsActivity)) {
                security += switchRow(R.drawable.ic_fingerprint, "Unlock with Biometrics", null, Lock.biometric(this@SettingsActivity)) {
                    Lock.setBiometric(this@SettingsActivity, it)
                }
            }
            if (lockOn) {
                security += row(R.drawable.ic_timer, "Auto-lock", Lock.timeouts[Lock.timeout(this@SettingsActivity)]) {
                    choose("Auto-lock", Lock.timeouts, Lock.timeout(this@SettingsActivity)) {
                        Lock.setTimeout(this@SettingsActivity, it)
                        render()
                    }
                }
            }
            addView(group("Security", security), lp(top = 24))

            val count = Store.contacts.size
            addView(group("Contacts Backup", listOf(
                vertical(
                    horizontal(
                        button("Import VCF", Btn.OUTLINED, R.drawable.ic_download) { importVcf() },
                        button("Export VCF", Btn.FILLED, R.drawable.ic_upload) { exportVcf() }.apply { enable(count > 0) },
                    ).apply {
                        for (i in 0..1) getChildAt(i).layoutParams = lp(0, weight = 1f).apply { if (i == 1) marginStart = dp(12) }
                    },
                ).apply {
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    if (count > 0) addView(text("$count contact${if (count == 1) "" else "s"} available", 13f, p.onSurfaceVariant).apply {
                        gravity = Gravity.CENTER
                    }, lp(top = 12))
                },
            )), lp(top = 24))

            addView(group("Useful Links", listOf(
                row(R.drawable.ic_book, "README", "Check out the app's README on GitHub") {
                    open("https://github.com/BioHazard786/Alternate/blob/main/README.md")
                },
                row(R.drawable.ic_bug, "GitHub Issues", "Create an issue on GitHub") {
                    open("https://github.com/BioHazard786/Alternate/issues")
                },
                row(R.drawable.ic_heart, "Support Development", "Think I deserve a coffee? Click here!") {
                    open("https://github.com/sponsors/BioHazard786")
                },
            )), lp(top = 24))

            addView(group("Developed by", listOf(
                row(R.drawable.ic_person, "Mohd Zaid", "Mail · GitHub · Telegram"),
                row(R.drawable.ic_email_outline, "Mail") { open("mailto:message@zaid.qzz.io") },
                row(R.drawable.ic_github, "GitHub") { open("https://github.com/BioHazard786") },
                row(R.drawable.ic_telegram, "Telegram") { open("https://t.me/lulu786") },
            )), lp(top = 24))

            val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
            addView(text("Alternate $version", 13f, p.onSurfaceVariant).apply { gravity = Gravity.CENTER }, lp(top = 24))
        }
        setScreen(vertical(bar, scroll(content)).apply { getChildAt(1).layoutParams = lp(h = 0, weight = 1f) })
    }

    private fun switchRow(iconRes: Int, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = Switch(this).apply {
            isChecked = checked
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(p.onPrimary, p.outline))
            trackTintList = ColorStateList(states, intArrayOf(p.primary, p.containerHigh))
            isClickable = false
            isFocusable = false
        }
        return row(iconRes, title, subtitle, trailing = sw) {
            sw.isChecked = !sw.isChecked
            onChange(sw.isChecked)
        }
    }

    private fun choose(title: String, options: Map<String, String>, current: String, onPick: (String) -> Unit) {
        val keys = options.keys.toList()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(options.values.toTypedArray(), keys.indexOf(current)) { d, i ->
                d.dismiss()
                onPick(keys[i])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pinInput(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(4))
        gravity = Gravity.CENTER
        textSize = 22f
    }

    private fun setupPin() {
        val first = pinInput("Enter a 4-digit PIN")
        val second = pinInput("Confirm PIN")
        val dialog = AlertDialog.Builder(this)
            .setTitle("Set passcode")
            .setView(vertical(first, second).apply { setPadding(dp(24), dp(8), dp(24), 0) })
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { render() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = first.text.toString()
                when {
                    pin.length != 4 -> first.error = "PIN must be 4 digits"
                    pin != second.text.toString() -> second.error = "PINs don't match"
                    else -> {
                        Lock.setPin(this, pin)
                        if (Lock.biometricAvailable(this)) Lock.setBiometric(this, true)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun disablePin() {
        val input = pinInput("Current PIN")
        val dialog = AlertDialog.Builder(this)
            .setTitle("Turn off passcode")
            .setMessage("Enter your current PIN")
            .setView(LinearLayout(this).apply {
                setPadding(dp(24), 0, dp(24), 0)
                addView(input, MATCH, WRAP)
            })
            .setPositiveButton("Turn off", null)
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { render() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when (Lock.verify(this, input.text.toString())) {
                    Lock.Result.OK -> {
                        Lock.clear(this)
                        dialog.dismiss()
                    }
                    Lock.Result.WRONG -> {
                        input.setText("")
                        input.error = "Invalid PIN. Please try again."
                    }
                    Lock.Result.LOCKED_OUT -> input.error = "Too many failed attempts. Please try again later."
                }
            }
        }
        dialog.show()
    }

    private fun importVcf() = runCatching {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/vcard", "text/x-vcard", "text/directory", "text/plain")), REQ_IMPORT)
    }

    private fun exportVcf() {
        if (Store.contacts.isEmpty()) {
            alert("No Contacts", "You don't have any contacts to export.")
            return
        }
        runCatching {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/x-vcard").putExtra(Intent.EXTRA_TITLE, Share.fileName()), REQ_EXPORT)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode == REQ_IMPORT) {
            val country = Phone.byIso(Phone.dialCountry(this)) ?: Phone.countries.first()
            Store.bg({
                contentResolver.openInputStream(uri)!!.use { Vcf.read(it.bufferedReader().readText(), country) }
            }) { r ->
                val found = r.getOrNull()
                if (found.isNullOrEmpty()) {
                    alert("Import Failed", if (r.isFailure) "Failed to import contacts from file" else "No contacts found in the selected file")
                    return@bg
                }
                Store.addAll(this, found) { added ->
                    if (added == null) alert("Import Failed", "Failed to import any contacts")
                    else alert("Import Successful", "$added contacts have been imported successfully from the VCF file")
                    render()
                }
            }
        } else if (requestCode == REQ_EXPORT) {
            val list = Store.contacts
            Store.bg({ contentResolver.openOutputStream(uri, "wt")!!.use { it.write(Vcf.write(list).toByteArray()) } }) { r ->
                if (r.isSuccess) alert("Export Successful", "${list.size} contacts have been exported to a VCF file.")
                else alert("Export Failed", "Failed to export contacts to file")
            }
        }
    }

    companion object {
        private const val REQ_IMPORT = 1
        private const val REQ_EXPORT = 2
    }
}
