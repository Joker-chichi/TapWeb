package com.tapweb.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.tapweb.R

object BrowserLauncher {

    fun open(context: Context, url: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .setToolbarColor(ContextCompat.getColor(context, R.color.primary))
            .build()

        try {
            customTabsIntent.launchUrl(context, Uri.parse(url))
        } catch (_: Exception) {
            fallbackToDefaultBrowser(context, url)
        }
    }

    private fun fallbackToDefaultBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            // No browser available
        }
    }
}
