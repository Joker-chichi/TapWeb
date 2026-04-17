package com.tapweb.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object FaviconManager {

    private const val DIR_FAVICONS = "favicons"

    fun saveCustomIcon(context: Context, id: Long, sourceUri: Uri): String? {
        return try {
            val dir = File(context.filesDir, DIR_FAVICONS)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "$id.png")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            // Resize to 96x96 for consistency
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
                if (scaled != bitmap) bitmap.recycle()
                FileOutputStream(file).use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                scaled.recycle()
            }

            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun getCustomIconFile(context: Context, id: Long): File? {
        val file = File(context.filesDir, "$DIR_FAVICONS/$id.png")
        return if (file.exists()) file else null
    }

    fun deleteCustomIcon(context: Context, id: Long) {
        val file = File(context.filesDir, "$DIR_FAVICONS/$id.png")
        if (file.exists()) file.delete()
    }

    fun buildDefaultFaviconUrl(url: String): String {
        val domain = UrlUtils.extractDomain(url)
        val scheme = try {
            java.net.URI(url).scheme ?: "https"
        } catch (_: Exception) {
            "https"
        }
        return "$scheme://$domain/favicon.ico"
    }
}
