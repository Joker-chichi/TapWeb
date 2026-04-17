package com.tapweb.shortcut

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.util.Log
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tapweb.R
import com.tapweb.ui.home.MainActivity
import com.tapweb.ui.webview.WebViewActivity
import com.tapweb.util.DeviceHelper

object ShortcutHelper {

    private const val TAG = "ShortcutHelper"

    /**
     * Result of a shortcut creation attempt.
     */
    enum class Result {
        PINNED,           // Standard API accepted (dialog may appear)
        DYNAMIC_ONLY,     // Only dynamic shortcut created (long-press menu)
        NOT_SUPPORTED,    // Launcher doesn't support pinning
        FAILED            // Exception occurred
    }

    fun create(
        context: Context,
        id: Long,
        title: String,
        url: String,
        faviconUrl: String?,
        customIconPath: String? = null
    ): Result {
        return try {
            val shortcut = buildShortcut(context, id, title, url, faviconUrl, customIconPath)

            // Step 1: Add dynamic shortcut (long-press menu — works everywhere)
            try {
                ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcut))
                Log.d(TAG, "Dynamic shortcut added: id=$id")
            } catch (e: Exception) {
                Log.w(TAG, "addDynamicShortcuts failed: ${e.message}")
            }

            // Step 2: Try standard pin shortcut API
            val pinSupported = ShortcutManagerCompat.isRequestPinShortcutSupported(context)
            Log.d(TAG, "isRequestPinShortcutSupported = $pinSupported")

            if (pinSupported) {
                val callback = PendingIntent.getBroadcast(
                    context,
                    id.toInt(),
                    Intent(context, ShortcutPinReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                ShortcutManagerCompat.requestPinShortcut(
                    context, shortcut, callback.intentSender
                )
                Log.d(TAG, "requestPinShortcut sent")
            }

            // Step 3: Targeted legacy broadcast to the specific launcher.
            // Setting the package makes this an explicit broadcast (not implicit),
            // which bypasses Android 8+ implicit broadcast restrictions.
            installShortcutTargeted(context, id, title, url, faviconUrl, customIconPath)

            when {
                pinSupported -> Result.PINNED
                else -> Result.DYNAMIC_ONLY
            }
        } catch (e: Exception) {
            Log.e(TAG, "create failed", e)
            Toast.makeText(context, "创建失败: ${e.message}", Toast.LENGTH_LONG).show()
            Result.FAILED
        }
    }

    fun update(
        context: Context,
        id: Long,
        title: String,
        url: String,
        faviconUrl: String?,
        customIconPath: String? = null
    ) {
        try {
            val shortcut = buildShortcut(context, id, title, url, faviconUrl, customIconPath)
            ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        } catch (_: Exception) {
        }
    }

    fun remove(context: Context, id: Long) {
        try {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(id.toString()))
        } catch (_: Exception) {
        }
    }

    /**
     * Send INSTALL_SHORTCUT broadcast targeted at the specific launcher package.
     * This converts the implicit broadcast to an explicit one (setPackage),
     * which bypasses the Android 8+ restriction on implicit broadcasts.
     */
    private fun installShortcutTargeted(
        context: Context,
        id: Long,
        title: String,
        url: String,
        faviconUrl: String?,
        customIconPath: String?
    ) {
        val launcherPackage = DeviceHelper.getLauncherPackage(context)
        Log.d(TAG, "Launcher package: $launcherPackage")

        val launchIntent = buildLaunchIntent(context, id, url, title)
        val bitmap = loadIconBitmap(faviconUrl, customIconPath, title)

        val intent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
            // Key fix: target the specific launcher to make this explicit
            launcherPackage?.let { setPackage(it) }
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, title)
            putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
            putExtra("duplicate", false)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "Targeted INSTALL_SHORTCUT broadcast sent to: $launcherPackage")
    }

    private fun buildShortcut(
        context: Context,
        id: Long,
        title: String,
        url: String,
        faviconUrl: String?,
        customIconPath: String?
    ): ShortcutInfoCompat {
        val bitmap = loadIconBitmap(faviconUrl, customIconPath, title)
        return ShortcutInfoCompat.Builder(context, id.toString())
            .setShortLabel(title)
            .setLongLabel(title)
            .setIcon(IconCompat.createWithBitmap(bitmap))
            .setIntent(buildLaunchIntent(context, id, url, title))
            .setActivity(ComponentName(context, MainActivity::class.java))
            .build()
    }

    private fun buildLaunchIntent(
        context: Context,
        id: Long,
        url: String,
        title: String
    ) = Intent(context, WebViewActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        // Unique data URI so intoExisting can match tasks by website ID
        data = tu'piaUri.parse("tapweb://site/$id")
        putExtra(WebViewActivity.EXTRA_WEBSITE_ID, id)
        putExtra(WebViewActivity.EXTRA_URL, url)
        putExtra(WebViewActivity.EXTRA_TITLE, title)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    /**
     * Load icon in priority: custom file → favicon URL → letter fallback.
     * Must be called from a background thread (coroutine).
     */
    private fun loadIconBitmap(
        faviconUrl: String?,
        customIconPath: String?,
        title: String
    ): Bitmap {
        // 1. Try custom icon file
        if (!customIconPath.isNullOrBlank()) {
            try {
                val file = java.io.File(customIconPath)
                if (file.exists()) {
                    val raw = BitmapFactory.decodeFile(file.absolutePath)
                    if (raw != null) return toRoundBitmap(raw, 96)
                }
            } catch (_: Exception) {
            }
        }

        // 2. Try favicon URL
        if (!faviconUrl.isNullOrBlank()) {
            try {
                val connection = java.net.URL(faviconUrl).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.getInputStream().use { input ->
                    val raw = BitmapFactory.decodeStream(input)
                    if (raw != null) return toRoundBitmap(raw, 96)
                }
            } catch (_: Exception) {
            }
        }

        // 3. Fallback: letter bitmap
        return createLetterBitmap(title)
    }

    private fun toRoundBitmap(src: Bitmap, size: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)

        if (scaled !== src) scaled.recycle()
        return output
    }

    private fun createLetterBitmap(title: String): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawCircle(
            size / 2f, size / 2f, size / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C9A92.toInt() }
        )

        val letter = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(letter, 0, letter.length, bounds)
        canvas.drawText(letter, size / 2f, size / 2f + bounds.height() / 2f, textPaint)

        return bitmap
    }
}
