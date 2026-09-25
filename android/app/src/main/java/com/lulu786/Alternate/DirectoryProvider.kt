package com.lulu786.Alternate

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract.Directory
import android.provider.ContactsContract.PhoneLookup
import android.util.Base64
import java.io.File

/**
 * Contacts directory so the system dialer / call log can show names for numbers saved in Alternate
 * without adding them to the real address book.
 */
class DirectoryProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    private lateinit var authority: String

    @Volatile
    private var photo = ""

    override fun onCreate(): Boolean {
        authority = context!!.packageName + ".directory"
        matcher.addURI(authority, "directories", DIRECTORIES)
        matcher.addURI(authority, "phone_lookup/*", PHONE_LOOKUP)
        matcher.addURI(authority, PHOTO_PATH, PHOTO)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? {
        val ctx = context ?: return null
        val cols = projection ?: return null
        return when (matcher.match(uri)) {
            DIRECTORIES -> MatrixCursor(cols).apply {
                val label = ctx.getString(R.string.app_name)
                addRow(cols.map {
                    when (it) {
                        Directory.ACCOUNT_NAME, Directory.ACCOUNT_TYPE, Directory.DISPLAY_NAME -> label
                        Directory.TYPE_RESOURCE_ID -> R.string.app_name
                        Directory.EXPORT_SUPPORT -> Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY
                        Directory.SHORTCUT_SUPPORT -> Directory.SHORTCUT_SUPPORT_NONE
                        else -> null
                    }
                })
            }
            PHONE_LOOKUP -> MatrixCursor(cols).apply {
                val c = Db.get(ctx).find(Phone.lookupKeys(ctx, uri.pathSegments[1]))
                photo = c?.photo ?: ""
                if (c != null) addRow(cols.map {
                    when (it) {
                        PhoneLookup._ID -> -1
                        PhoneLookup.DISPLAY_NAME -> c.displayName
                        PhoneLookup.LABEL -> listOf(c.appointment, c.location).filter { v -> v.isNotEmpty() }
                            .joinToString(", ").ifEmpty { "Mobile" }
                        PhoneLookup.NUMBER -> c.fullPhoneNumber
                        PhoneLookup.NORMALIZED_NUMBER -> c.phoneNumber
                        PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup.PHOTO_URI ->
                            if (c.photo.isNotEmpty()) Uri.parse("content://$authority/$PHOTO_PATH") else null
                        else -> null
                    }
                })
            }
            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != PHOTO || photo.isEmpty()) return null
        val ctx = context ?: return null
        return runCatching {
            val file = File(ctx.cacheDir, "caller_photo_${photo.hashCode()}.jpg")
            if (!file.exists()) {
                ctx.cacheDir.listFiles { f -> f.name.startsWith("caller_photo_") }?.forEach { it.delete() }
                file.writeBytes(Base64.decode(photo.substringAfter(','), Base64.DEFAULT))
            }
            AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }.getOrNull()
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        private const val DIRECTORIES = 1
        private const val PHONE_LOOKUP = 2
        private const val PHOTO = 3
        private const val PHOTO_PATH = "photo/primary_photo"
    }
}
