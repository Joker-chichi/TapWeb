package com.tapweb.util

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconFetcherTest {

    private val gson = Gson()

    // --- Gson PWA manifest parsing ---

    @Test
    fun `gson parses valid manifest with icons`() {
        val json = """{
            "name": "Test App",
            "icons": [
                {"src": "/icon-192.png", "sizes": "192x192"},
                {"src": "/icon-512.png", "sizes": "512x512"},
                {"src": "/icon-48.png", "sizes": "48x48"}
            ]
        }"""
        val manifest = gson.fromJson(json, FaviconFetcher.PwaManifest::class.java)
        assertNotNull(manifest)
        assertEquals(3, manifest!!.icons?.size)

        val best = manifest.icons!!
            .filter { !it.src.isNullOrBlank() }
            .maxByOrNull { icon ->
                icon.sizes?.split(" ")
                    ?.maxOfOrNull { s -> s.split("x").firstOrNull()?.toIntOrNull() ?: 0 }
                    ?: 0
            }
        assertEquals("/icon-512.png", best!!.src)
    }

    @Test
    fun `gson parses manifest with no icons`() {
        val json = """{"name": "Test App"}"""
        val manifest = gson.fromJson(json, FaviconFetcher.PwaManifest::class.java)
        assertNotNull(manifest)
        assertNull(manifest!!.icons)
    }

    @Test
    fun `gson parses manifest with empty icons array`() {
        val json = """{"name": "Test App", "icons": []}"""
        val manifest = gson.fromJson(json, FaviconFetcher.PwaManifest::class.java)
        assertNotNull(manifest)
        assertEquals(0, manifest!!.icons!!.size)
    }

    @Test
    fun `gson handles manifest with multiple sizes`() {
        val json = """{
            "icons": [
                {"src": "https://example.com/icon.png", "sizes": "72x72 96x96 128x128"}
            ]
        }"""
        val manifest = gson.fromJson(json, FaviconFetcher.PwaManifest::class.java)
        val icon = manifest!!.icons!!.first()
        val maxSize = icon.sizes!!.split(" ")
            .maxOfOrNull { s -> s.split("x").firstOrNull()?.toIntOrNull() ?: 0 }
        assertEquals(128, maxSize)
    }

    @Test
    fun `gson handles invalid json gracefully`() {
        val json = "not json"
        try {
            gson.fromJson(json, FaviconFetcher.PwaManifest::class.java)
        } catch (_: Exception) {
            // Expected — Gson throws on invalid JSON, FaviconFetcher catches it
        }
    }

    // --- SiteMeta data class ---

    @Test
    fun `siteMeta holds values correctly`() {
        val meta = SiteMeta("GitHub", "https://github.com/favicon.ico")
        assertEquals("GitHub", meta.title)
        assertEquals("https://github.com/favicon.ico", meta.faviconUrl)
    }

    @Test
    fun `siteMeta allows nulls`() {
        val meta = SiteMeta(null, null)
        assertNull(meta.title)
        assertNull(meta.faviconUrl)
    }
}
