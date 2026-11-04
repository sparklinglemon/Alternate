package com.lulu786.Alternate

import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Intents.Insert
import android.provider.OpenableColumns
import android.util.Base64
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Share {
    fun fileName() = "contacts_" + SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.US).format(Date()) + ".vcf"

    /** Shares contacts as a .vcf file through [ShareProvider]. */
    fun contacts(ctx: Context, list: List<Contact>) {
        if (list.isEmpty()) return
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName())
        file.writeText(Vcf.write(list))
        val uri = Uri.parse("content://${ctx.packageName}.share/${file.name}")
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/x-vcard")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        ctx.startActivity(Intent.createChooser(send, "Share contact").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Opens Google Contacts' "new contact" screen pre-filled with [c] (the system contacts app if Google Contacts
     * isn't installed). Returns false when no contacts app can take it.
     */
    fun toContacts(ctx: Context, c: Contact): Boolean {
        val rows = ArrayList<ContentValues>()
        fun row(mime: String, vararg fields: Pair<String, Any>) {
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, mime)
                fields.forEach { (key, value) ->
                    when (value) {
                        is ByteArray -> put(key, value)
                        is Int -> put(key, value)
                        else -> put(key, value.toString())
                    }
                }
            }
        }
        if (c.nickname.isNotEmpty()) row(CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, CommonDataKinds.Nickname.NAME to c.nickname)
        if (c.website.isNotEmpty()) row(CommonDataKinds.Website.CONTENT_ITEM_TYPE, CommonDataKinds.Website.URL to c.website)
        if (c.birthday.isNotEmpty()) row(
            CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            CommonDataKinds.Event.START_DATE to c.birthday,
            CommonDataKinds.Event.TYPE to CommonDataKinds.Event.TYPE_BIRTHDAY,
        )
        if (c.photo.isNotEmpty()) runCatching { Base64.decode(c.photo.substringAfter(','), Base64.DEFAULT) }.getOrNull()
            ?.let { row(CommonDataKinds.Photo.CONTENT_ITEM_TYPE, CommonDataKinds.Photo.PHOTO to it) }

        // Prefix and suffix go into the name; the contacts app splits them back out.
        val name = listOf(c.prefix, c.name, c.suffix).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        val insert = Intent(Insert.ACTION, ContactsContract.Contacts.CONTENT_URI)
            .putExtra(Insert.NAME, name)
            .putExtra(Insert.PHONE, "+${c.fullPhoneNumber}")
            .putExtra(Insert.PHONE_TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
            .putExtra("finishActivityOnSaveCompleted", true)
            .putParcelableArrayListExtra(Insert.DATA, rows)
        if (c.email.isNotEmpty()) insert.putExtra(Insert.EMAIL, c.email).putExtra(Insert.EMAIL_TYPE, CommonDataKinds.Email.TYPE_HOME)
        if (c.location.isNotEmpty()) insert.putExtra(Insert.POSTAL, c.location)
            .putExtra(Insert.POSTAL_TYPE, CommonDataKinds.StructuredPostal.TYPE_HOME)
        if (c.appointment.isNotEmpty()) insert.putExtra(Insert.JOB_TITLE, c.appointment)
        if (c.notes.isNotEmpty()) insert.putExtra(Insert.NOTES, c.notes)

        return listOf(GOOGLE_CONTACTS, null).any { target -> runCatching { ctx.startActivity(Intent(insert).setPackage(target)) }.isSuccess }
    }

    private const val GOOGLE_CONTACTS = "com.google.android.contacts"
}

/** Minimal read-only provider for files in cache/share (a tiny stand-in for AndroidX FileProvider). */
class ShareProvider : ContentProvider() {
    private fun file(uri: Uri): File? {
        val name = uri.lastPathSegment?.let { File(it).name } ?: return null
        return File(File(context!!.cacheDir, "share"), name).takeIf { it.isFile }
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? {
        val f = file(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols, 1).apply {
            addRow(cols.map {
                when (it) {
                    OpenableColumns.DISPLAY_NAME -> f.name
                    OpenableColumns.SIZE -> f.length()
                    else -> null
                }
            })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
        file(uri)?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }

    override fun getType(uri: Uri) = "text/x-vcard"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
