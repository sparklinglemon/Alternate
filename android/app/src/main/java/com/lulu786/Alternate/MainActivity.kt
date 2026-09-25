package com.lulu786.Alternate

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class MainActivity : BaseActivity() {
    private sealed class Row {
        class Header(val letter: String) : Row()
        class Item(val contact: Contact, val index: Int, val first: Boolean, val last: Boolean) : Row()
    }

    private lateinit var bar: Bar
    private lateinit var search: EditText
    private lateinit var fab: TextView
    private lateinit var empty: LinearLayout
    private lateinit var emptyText: TextView
    private val rows = ArrayList<Row>()
    private val selected = LinkedHashMap<String, Contact>()
    private var searching = false
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bar = Bar(this, large = true)
        search = EditText(this).apply {
            hint = "Search contacts"
            background = null
            textSize = 18f
            isSingleLine = true
            setTextColor(p.onSurface)
            setHintTextColor(p.onSurfaceVariant)
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = refresh()
            })
        }
        bar.addView(search, 1, LinearLayout.LayoutParams(0, WRAP, 1f))

        val list = ListView(this).apply {
            adapter = this@MainActivity.adapter
            divider = null
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            clipToPadding = false
            setPadding(dp(16), dp(8), dp(16), dp(96))
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        }
        emptyText = text("", 16f, p.onSurfaceVariant).apply { gravity = Gravity.CENTER }
        empty = vertical(text("No contacts found", 24f).apply { gravity = Gravity.CENTER }, emptyText).apply {
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), dp(64))
        }
        fab = TextView(this).apply {
            gravity = Gravity.CENTER
            minWidth = dp(60)
            minHeight = dp(60)
            textSize = 16f
            setTextColor(p.onSecondaryContainer)
            compoundDrawablePadding = dp(8)
            elevation = dp(3).toFloat()
            val bg = shape(p.secondaryContainer, dp(18).toFloat())
            background = ripple(bg, bg)
        }
        setScreen(FrameLayout(this).apply {
            addView(vertical(bar, FrameLayout(this@MainActivity).apply {
                addView(list, MATCH, MATCH)
                addView(empty, MATCH, MATCH)
            }).apply { getChildAt(1).layoutParams = lp(h = 0, weight = 1f) })
            addView(fab, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(20), dp(20))
            })
        })

        if (savedInstanceState == null) {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("Alternate needs permission to draw over other apps to show caller info during calls. Allow it now?")
                .setPositiveButton("Yes") { _, _ ->
                    runCatching {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        Store.load(this) { refresh() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = when {
        selected.isNotEmpty() -> { selected.clear(); refresh() }
        searching -> setSearching(false)
        else -> @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun setSearching(on: Boolean) {
        searching = on
        search.setText("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (on) {
            search.visibility = View.VISIBLE
            search.requestFocus()
            search.post { imm.showSoftInput(search, 0) }
        } else {
            imm.hideSoftInputFromWindow(search.windowToken, 0)
        }
        refresh()
    }

    private fun matches(c: Contact, tokens: List<String>): Boolean {
        val words = listOf(
            listOf(c.prefix, c.name, c.suffix).filter { it.isNotEmpty() }.joinToString(" "),
            c.location, c.appointment, c.nickname, c.email, "+" + c.fullPhoneNumber, c.phoneNumber,
        ).joinToString(" ").lowercase()
        if (tokens.size == 1) return words.contains(tokens[0])
        val parts = words.split(Regex("\\s+"))
        return tokens.all { t -> parts.any { it.contains(t) } }
    }

    private fun refresh() {
        val query = search.text.toString().trim().lowercase()
        val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val shown = when {
            !searching -> Store.contacts
            tokens.isEmpty() -> emptyList()
            else -> Store.contacts.filter { matches(it, tokens) }
        }
        rows.clear()
        shown.groupBy { it.letter }.toSortedMap().forEach { (letter, group) ->
            rows += Row.Header(letter)
            group.forEachIndexed { i, c -> rows += Row.Item(c, i, i == 0, i == group.lastIndex) }
        }
        adapter.notifyDataSetChanged()
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (searching) "Type a name, number or email to search" else "Add your first contact"
        // Drop selections for contacts that no longer exist.
        selected.keys.retainAll(Store.contacts.mapTo(HashSet()) { it.fullPhoneNumber })
        updateBar()
    }

    private fun updateBar() {
        bar.clearActions()
        val selecting = selected.isNotEmpty()
        bar.nav.visibility = if (selecting || searching) View.VISIBLE else View.GONE
        bar.title.visibility = if (searching && !selecting) View.GONE else View.VISIBLE
        search.visibility = if (searching && !selecting) View.VISIBLE else View.GONE
        bar.title.textSize = if (selecting) 22f else 28f
        when {
            selecting -> {
                bar.nav.setImageResource(R.drawable.ic_close)
                bar.title.text = "${selected.size} selected"
                bar.action(R.drawable.ic_copy, "Copy") { copySelected() }
                bar.action(R.drawable.ic_delete, "Delete") { deleteSelected() }
            }
            searching -> bar.nav.setImageResource(R.drawable.ic_back)
            else -> {
                bar.title.text = "Contacts"
                if (Lock.enabled(this)) bar.action(R.drawable.ic_lock_open, "Lock now") {
                    Lock.lockNow(this)
                    startActivity(Intent(this, LockActivity::class.java))
                }
                bar.action(R.drawable.ic_search, "Search") { setSearching(true) }
                bar.action(R.drawable.ic_settings, "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) }
            }
        }
        fab.visibility = if (searching && !selecting) View.GONE else View.VISIBLE
        if (selecting) {
            fab.text = "Share"
            fab.setPadding(dp(20), 0, dp(24), 0)
            fab.setCompoundDrawablesRelative(drawable(R.drawable.ic_share, p.onSecondaryContainer), null, null, null)
            fab.contentDescription = "Share"
            fab.setOnClickListener {
                Share.contacts(this, selected.values.toList())
                selected.clear()
                refresh()
            }
        } else {
            fab.text = ""
            fab.setPadding(0, 0, 0, 0)
            fab.setCompoundDrawablesRelative(drawable(R.drawable.ic_add, p.onSecondaryContainer), null, null, null)
            fab.contentDescription = "Add contact"
            fab.setOnClickListener { startActivity(Intent(this, EditActivity::class.java)) }
        }
    }

    private fun copySelected() {
        val text = selected.values.joinToString("\n\n") { c ->
            listOf(
                "name" to c.displayName, "number" to "+${c.fullPhoneNumber}", "email" to c.email,
                "appointment" to c.appointment, "location" to c.location, "notes" to c.notes,
                "nickname" to c.nickname, "website" to c.website, "birthday" to c.birthday,
                "labels" to c.labels, "prefix" to c.prefix, "suffix" to c.suffix,
            ).filter { it.second.isNotBlank() }.joinToString("\n") { "${it.first} - ${it.second}" }
        }
        val n = selected.size
        copy(text, "$n contact${if (n == 1) "" else "s"} copied to clipboard")
        selected.clear()
        refresh()
    }

    private fun deleteSelected() = alert(
        "Delete contacts?", "These contacts will be permanently deleted from your device", "Delete", "Cancel",
    ) {
        Store.delete(this, selected.keys.toList()) { ok ->
            if (ok) selected.clear() else toast("Failed to delete contacts")
            refresh()
        }
    }

    private fun toggle(c: Contact) {
        if (selected.remove(c.fullPhoneNumber) == null) selected[c.fullPhoneNumber] = c
        refresh()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1
        override fun isEnabled(position: Int) = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = rows[position]
            if (row is Row.Header) {
                val v = convertView as? TextView ?: text("", 14f, p.primary, true).apply {
                    setPadding(dp(8), dp(16), dp(8), dp(8))
                }
                v.text = row.letter
                return v
            }
            row as Row.Item
            val holder = (convertView?.tag as? ItemHolder) ?: ItemHolder()
            holder.bind(row)
            return holder.root
        }
    }

    private inner class ItemHolder {
        val avatar = Avatar(this@MainActivity)
        val name = text("", 17f, p.onSurface)
        val number = text("", 14f, p.outline)
        val inner = horizontal(avatar, vertical(name, number)).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            avatar.layoutParams = LinearLayout.LayoutParams(dp(45), dp(45))
            getChildAt(1).layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(16) }
            name.isSingleLine = true
            number.isSingleLine = true
        }
        val root = FrameLayout(this@MainActivity).apply {
            layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
            addView(inner, FrameLayout.LayoutParams(MATCH, WRAP))
            tag = this@ItemHolder
        }

        fun bind(row: Row.Item) {
            val c = row.contact
            val isSelected = selected.containsKey(c.fullPhoneNumber)
            val big = dp(16).toFloat()
            val small = dp(4).toFloat()
            val t = if (row.first) big else small
            val b = if (row.last) big else small
            val radii = floatArrayOf(t, t, t, t, b, b, b, b)
            inner.background = ripple(
                shape(if (isSelected) p.onSurfaceVariant else p.container, radii = radii), shape(Color.BLACK, radii = radii),
            )
            root.setPadding(0, 0, 0, if (row.last) 0 else dp(2))
            name.text = c.displayName
            name.setTextColor(if (isSelected) p.surface else p.onSurface)
            number.text = Phone.format(c)
            number.setTextColor(if (isSelected) p.surface else p.outline)
            if (isSelected) {
                avatar.bitmap = null
                avatar.bg = p.primaryContainer
                avatar.fg = p.onPrimaryContainer
                avatar.icon = getDrawable(R.drawable.ic_check)
                avatar.invalidate()
            } else {
                avatar.show(c, row.index, p.dark)
            }
            inner.setOnClickListener {
                if (selected.isNotEmpty()) toggle(c)
                else startActivity(Intent(this@MainActivity, ContactActivity::class.java)
                    .putExtra("number", c.fullPhoneNumber).putExtra("index", row.index))
            }
            inner.setOnLongClickListener { toggle(c); true }
        }
    }
}
