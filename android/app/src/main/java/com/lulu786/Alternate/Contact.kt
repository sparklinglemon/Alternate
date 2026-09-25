package com.lulu786.Alternate

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

data class Contact(
    val fullPhoneNumber: String, // dial code + national number, digits only
    val phoneNumber: String, // national number
    val countryCode: String, // ISO 3166 alpha-2
    val name: String,
    val appointment: String = "",
    val location: String = "",
    val iosRow: String = "",
    val suffix: String = "",
    val prefix: String = "",
    val email: String = "",
    val notes: String = "",
    val website: String = "",
    val birthday: String = "", // YYYY-MM-DD or --MM-DD
    val labels: String = "",
    val nickname: String = "",
    val photo: String = "", // data:image/jpeg;base64,...
) {
    val displayName: String
        get() = buildString {
            prefix.trim().takeIf { it.isNotEmpty() }?.let { append(it).append(' ') }
            append(name.trim())
            suffix.trim().takeIf { it.isNotEmpty() }?.let { append(", ").append(it) }
        }

    val letter: String get() = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"

    fun toValues() = ContentValues().apply {
        put("fullPhoneNumber", fullPhoneNumber); put("phoneNumber", phoneNumber)
        put("countryCode", countryCode); put("name", name); put("appointment", appointment)
        put("location", location); put("iosRow", iosRow); put("suffix", suffix); put("prefix", prefix)
        put("email", email); put("notes", notes); put("website", website); put("birthday", birthday)
        put("labels", labels); put("nickname", nickname); put("photo", photo)
    }

    companion object {
        fun from(c: Cursor): Contact {
            fun s(col: String) = c.getColumnIndex(col).let { if (it < 0) "" else c.getString(it) ?: "" }
            return Contact(
                s("fullPhoneNumber"), s("phoneNumber"), s("countryCode"), s("name"), s("appointment"),
                s("location"), s("iosRow"), s("suffix"), s("prefix"), s("email"), s("notes"), s("website"),
                s("birthday"), s("labels"), s("nickname"), s("photo"),
            )
        }
    }
}

/**
 * Same file, table and schema version as the previous Room database, so existing contacts carry over.
 */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "caller_database", null, 4) {
    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS caller_info (fullPhoneNumber TEXT PRIMARY KEY NOT NULL, " +
                "phoneNumber TEXT NOT NULL DEFAULT '', countryCode TEXT NOT NULL, name TEXT NOT NULL, " +
                "appointment TEXT NOT NULL, location TEXT NOT NULL, iosRow TEXT NOT NULL, " +
                "suffix TEXT NOT NULL DEFAULT '', prefix TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '', " +
                "notes TEXT NOT NULL DEFAULT '', website TEXT NOT NULL DEFAULT '', birthday TEXT NOT NULL DEFAULT '', " +
                "labels TEXT NOT NULL DEFAULT '', nickname TEXT NOT NULL DEFAULT '', photo TEXT NOT NULL DEFAULT '')"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS caller_info")
            onCreate(db)
            return
        }
        if (oldVersion < 3) {
            // v2 stored the full number as phoneNumber and had a "city" column.
            val old = db.rawQuery("SELECT * FROM caller_info", null).use { c ->
                generateSequence { if (c.moveToNext()) c else null }.map {
                    fun s(col: String) = c.getColumnIndex(col).let { i -> if (i < 0) "" else c.getString(i) ?: "" }
                    val full = s("phoneNumber")
                    val dial = Phone.byIso(s("countryCode"))?.dial
                    Contact(
                        full, if (dial != null && full.startsWith(dial)) full.substring(dial.length) else full,
                        s("countryCode"), s("name"), s("appointment"), s("city"), s("iosRow"),
                    )
                }.toList()
            }
            db.execSQL("DROP TABLE caller_info")
            onCreate(db)
            old.forEach { db.insertWithOnConflict("caller_info", null, it.toValues(), SQLiteDatabase.CONFLICT_REPLACE) }
            return
        }
        if (oldVersion < 4) db.execSQL("ALTER TABLE caller_info ADD COLUMN photo TEXT NOT NULL DEFAULT ''")
    }

    fun all(): List<Contact> = readableDatabase.rawQuery("SELECT * FROM caller_info", null).use { c ->
        val list = ArrayList<Contact>(c.count)
        while (c.moveToNext()) list.add(Contact.from(c))
        list
    }

    fun find(keys: List<String>): Contact? {
        if (keys.isEmpty()) return null
        val q = keys.joinToString(",") { "?" }
        val args = (keys + keys).toTypedArray()
        return readableDatabase.rawQuery(
            "SELECT * FROM caller_info WHERE fullPhoneNumber IN ($q) OR phoneNumber IN ($q) LIMIT 1", args
        ).use { if (it.moveToFirst()) Contact.from(it) else null }
    }

    fun put(contacts: List<Contact>) = tx { db ->
        contacts.forEach { db.insertWithOnConflict("caller_info", null, it.toValues(), SQLiteDatabase.CONFLICT_REPLACE) }
    }

    fun delete(numbers: List<String>) = tx { db ->
        numbers.forEach { db.delete("caller_info", "fullPhoneNumber = ?", arrayOf(it)) }
    }

    private fun tx(block: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            block(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(ctx: Context): Db =
            instance ?: synchronized(this) { instance ?: Db(ctx.applicationContext).also { instance = it } }
    }
}

/** In-memory contact list shared by the screens, sorted by name. Writes go through here. */
object Store {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var contacts: List<Contact> = emptyList()
        private set

    fun <T> bg(work: () -> T, done: (Result<T>) -> Unit) {
        io.execute {
            val r = runCatching(work)
            main.post { done(r) }
        }
    }

    private fun sort(list: List<Contact>) = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun byNumber(full: String?) = contacts.firstOrNull { it.fullPhoneNumber == full }

    fun load(ctx: Context, done: () -> Unit) = bg({ sort(Db.get(ctx).all()) }) { r ->
        r.getOrNull()?.let { contacts = it }
        done()
    }

    /** Saves [contact], replacing [original] (its old number) if the number changed. */
    fun save(ctx: Context, contact: Contact, original: String?, done: (Boolean) -> Unit) = bg({
        val db = Db.get(ctx)
        if (original != null && original != contact.fullPhoneNumber) db.delete(listOf(original))
        db.put(listOf(contact))
    }) { r ->
        if (r.isSuccess) {
            contacts = sort(contacts.filter { it.fullPhoneNumber != original && it.fullPhoneNumber != contact.fullPhoneNumber } + contact)
        }
        done(r.isSuccess)
    }

    /** Adds contacts whose numbers aren't stored yet; returns how many were added. */
    fun addAll(ctx: Context, list: List<Contact>, done: (Int?) -> Unit) {
        val existing = contacts.mapTo(HashSet()) { it.fullPhoneNumber }
        val fresh = list.filter { existing.add(it.fullPhoneNumber) }
        bg({ Db.get(ctx).put(fresh) }) { r ->
            if (r.isSuccess) contacts = sort(contacts + fresh)
            done(if (r.isSuccess) fresh.size else null)
        }
    }

    fun delete(ctx: Context, numbers: List<String>, done: (Boolean) -> Unit) = bg({ Db.get(ctx).delete(numbers) }) { r ->
        if (r.isSuccess) {
            val gone = numbers.toSet()
            contacts = contacts.filter { it.fullPhoneNumber !in gone }
        }
        done(r.isSuccess)
    }
}
