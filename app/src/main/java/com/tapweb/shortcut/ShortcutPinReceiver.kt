package com.tapweb.shortcut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.tapweb.R

/**
 * Receives the callback when a pinned shortcut is confirmed by the user.
 * The system sends this via PendingIntent after the user accepts the pin dialog.
 */
class ShortcutPinReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, R.string.shortcut_created, Toast.LENGTH_SHORT).show()
    }
}
