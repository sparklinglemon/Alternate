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
import android.provider.OpenableColumns
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
