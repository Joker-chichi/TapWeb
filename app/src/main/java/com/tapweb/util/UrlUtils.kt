package com.tapweb.util

import java.net.URI
import java.net.URISyntaxException

object UrlUtils {

    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""

        // Already has scheme
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        // Common pattern: scheme://host without proper scheme
        if (trimmed.startsWith("://")) {
            return "https$trimmed"
        }

        return "https://$trimmed"
    }

    fun isValid(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val uri = URI(url)
            val host = uri.host
            when {
                host == null -> false
                host.contains(".") -> true
                host == "localhost" -> true
                else -> false
            }
        } catch (_: URISyntaxException) {
            false
        }
    }

    fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            uri.host ?: url
        } catch (_: Exception) {
            url
        }
    }

    fun extractPath(url: String): String {
        return try {
            val uri = URI(url)
            val path = uri.path ?: ""
            val query = uri.query
            when {
                query != null && path.isNotBlank() -> "$path?$query"
                query != null -> "?$query"
                else -> path
            }
        } catch (_: Exception) {
            url
        }
    }
}
