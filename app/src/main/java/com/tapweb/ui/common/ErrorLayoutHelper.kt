package com.tapweb.ui.common

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.tapweb.R

class ErrorLayoutHelper(private val errorContainer: View) {

    private val ivIcon: ImageView = errorContainer.findViewById(R.id.ivErrorIcon)
    private val tvTitle: TextView = errorContainer.findViewById(R.id.tvErrorTitle)
    private val tvMessage: TextView = errorContainer.findViewById(R.id.tvErrorMessage)
    private val btnRetry: View = errorContainer.findViewById(R.id.btnRetry)
    private val btnOpenBrowser: View = errorContainer.findViewById(R.id.btnOpenBrowser)

    enum class ErrorType {
        NO_NETWORK,
        LOAD_FAILED,
        TIMEOUT
    }

    fun show(type: ErrorType, onRetry: () -> Unit, onOpenBrowser: () -> Unit) {
        errorContainer.visibility = View.VISIBLE

        val (icon, title, message) = when (type) {
            ErrorType.NO_NETWORK -> Triple(
                R.drawable.ic_error,
                R.string.error_no_network,
                R.string.error_no_network_msg
            )
            ErrorType.LOAD_FAILED -> Triple(
                R.drawable.ic_error,
                R.string.error_load_failed,
                R.string.error_load_failed_msg
            )
            ErrorType.TIMEOUT -> Triple(
                R.drawable.ic_error,
                R.string.error_timeout,
                R.string.error_timeout_msg
            )
        }

        ivIcon.setImageResource(icon)
        tvTitle.setText(title)
        tvMessage.setText(message)

        btnRetry.setOnClickListener { onRetry() }
        btnOpenBrowser.setOnClickListener { onOpenBrowser() }
    }

    fun hide() {
        errorContainer.visibility = View.GONE
    }
}
