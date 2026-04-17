package com.tapweb.util

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class SiteMeta(val title: String?, val faviconUrl: String?)

object FaviconFetcher {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val TIMEOUT_MS = 8000
    private val gson = Gson()

    suspend fun fetch(url: String): SiteMeta = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()

            val title = doc.title().ifBlank { null }
            val favicon = resolveFavicon(doc, url)
            SiteMeta(title, favicon)
        } catch (_: Exception) {
            SiteMeta(null, googleS2Fallback(url))
        }
    }

    /**
     * Resolve favicon with PWA manifest support:
     * 1. Check <link rel="manifest"> and parse JSON manifest with Gson
     * 2. Check standard favicon links (prefer apple-touch-icon for higher res)
     * 3. Fallback to Google S2 API
     */
    private fun resolveFavicon(doc: org.jsoup.nodes.Document, baseUrl: String): String? {
        // PWA manifest — highest quality icons
        val manifestHref = doc.select("link[rel=manifest]").firstOrNull()?.attr("href")
        if (!manifestHref.isNullOrBlank()) {
            val manifestUrl = if (manifestHref.startsWith("http")) manifestHref
                else resolveRelative(baseUrl, manifestHref)
            val manifestIcon = fetchManifestIcon(manifestUrl)
            if (manifestIcon != null) return manifestIcon
        }

        // Standard favicon links — prefer apple-touch-icon (usually higher res)
        val links = doc.select("link[rel~=(?i)^(icon|shortcut icon|apple-touch-icon)]")
        val preferred = links.firstOrNull {
            it.attr("rel").contains("apple-touch-icon", ignoreCase = true)
        } ?: links.firstOrNull {
            it.attr("rel").contains("shortcut icon", ignoreCase = true)
        } ?: links.firstOrNull()

        preferred?.let { link ->
            val href = link.attr("href")
            if (href.isNotBlank()) {
                return if (href.startsWith("http")) href
                else resolveRelative(baseUrl, href)
            }
        }

        return googleS2Fallback(baseUrl)
    }

    /** Parse PWA manifest JSON with Gson — reliable instead of regex */
    private fun fetchManifestIcon(manifestUrl: String): String? {
        return try {
            val conn = java.net.URL(manifestUrl).openConnection()
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            val json = conn.getInputStream().bufferedReader().readText()
            val manifest = gson.fromJson(json, PwaManifest::class.java) ?: return null

            manifest.icons
                ?.filter { !it.src.isNullOrBlank() }
                ?.maxByOrNull { icon ->
                    icon.sizes?.split(" ")
                        ?.maxOfOrNull { s -> s.split("x").firstOrNull()?.toIntOrNull() ?: 0 }
                        ?: 0
                }
                ?.src
                ?.let { src ->
                    if (src.startsWith("http")) src
                    else resolveRelative(manifestUrl, src)
                }
        } catch (_: Exception) {
            null
        }
    }

    /** Gson data classes for PWA manifest */
    private data class PwaManifest(
        val icons: List<PwaIcon>? = null
    )

    private data class PwaIcon(
        val src: String? = null,
        val sizes: String? = null
    )

    private fun googleS2Fallback(url: String): String {
        val domain = UrlUtils.extractDomain(url)
        return "https://www.google.com/s2/favicons?domain=$domain&sz=64"
    }

    private fun resolveRelative(baseUrl: String, relative: String): String {
        return try {
            val base = java.net.URI(baseUrl)
            base.resolve(relative).toString()
        } catch (_: Exception) {
            relative
        }
    }
}
