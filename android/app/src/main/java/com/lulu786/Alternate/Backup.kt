package com.lulu786.Alternate

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Automatic backups: after every change the whole list is written as a timestamped vCard into a folder the user
 * picked (Storage Access Framework), and only the newest [KEEP] backups are kept.
 */
object Backup {
    private const val PREFIX = "alternate_backup_"
    private const val KEEP = 3
    private const val KEY_FOLDER = "backup_folder"
    private const val KEY_LAST = "backup_last"
    private const val KEY_ERROR = "backup_error"
    private const val FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun folder(ctx: Context): Uri? = ctx.settings().getString(KEY_FOLDER, null)?.let(Uri::parse)

    fun enabled(ctx: Context) = folder(ctx) != null

    fun lastBackup(ctx: Context) = ctx.settings().getLong(KEY_LAST, 0)

    fun lastError(ctx: Context): String? = ctx.settings().getString(KEY_ERROR, null)

    /** Picks [tree] as the backup folder (null turns automatic backups off), keeping access across reboots. */
    fun setFolder(ctx: Context, tree: Uri?) {
        val resolver = ctx.contentResolver
        folder(ctx)?.takeIf { it != tree }?.let { old -> runCatching { resolver.releasePersistableUriPermission(old, FLAGS) } }
        tree?.let { resolver.takePersistableUriPermission(it, FLAGS) }
        ctx.settings().edit().putString(KEY_FOLDER, tree?.toString()).remove(KEY_LAST).remove(KEY_ERROR).apply()
    }

    fun folderName(ctx: Context): String? {
        val tree = folder(ctx) ?: return null
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return runCatching {
            ctx.contentResolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').ifEmpty { null }
    }

    /** Backs up after a change. Skipped when the list is empty, so deleting everything never rotates out real backups. */
    fun changed(ctx: Context, list: List<Contact>) {
        if (enabled(ctx) && list.isNotEmpty()) now(ctx.applicationContext, list) {}
    }

    /** Writes a backup of [list] right away; [done] gets the file name. */
    fun now(ctx: Context, list: List<Contact>, done: (Result<String>) -> Unit) = Store.bg({ write(ctx, list) }) { r ->
        val edit = ctx.settings().edit()
        if (r.isSuccess) edit.putLong(KEY_LAST, System.currentTimeMillis()).remove(KEY_ERROR)
        else edit.putString(KEY_ERROR, describe(r.exceptionOrNull()))
        edit.apply()
        done(r)
    }

    private fun describe(e: Throwable?) = when (e) {
        is SecurityException -> "Alternate lost access to the backup folder. Choose it again."
        else -> e?.message ?: "Unknown error"
    }

    private fun write(ctx: Context, list: List<Contact>): String {
        val tree = folder(ctx) ?: error("No backup folder chosen")
        val resolver = ctx.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + ".vcf"
        // Two changes within the same second overwrite that second's file instead of creating "name (1).vcf".
        val target = backups(resolver, tree)[name]
            ?: DocumentsContract.createDocument(resolver, parent, "text/x-vcard", name)
            ?: error("Couldn't create the backup file")
        resolver.openOutputStream(target, "wt")!!.use { it.write(Vcf.write(list).toByteArray()) }
        val all = backups(resolver, tree)
        all.keys.sortedDescending().drop(KEEP).forEach { old ->
            runCatching { DocumentsContract.deleteDocument(resolver, all.getValue(old)) }
        }
        return name
    }

    /** Backup files in the folder by name; other files are never touched. Names sort oldest to newest. */
    private fun backups(resolver: ContentResolver, tree: Uri): Map<String, Uri> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = HashMap<String, Uri>()
        resolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(PREFIX) && name.endsWith(".vcf")) {
                    found[name] = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
        }
        return found
    }
}
