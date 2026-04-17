package com.tapweb.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconFetcherTest {

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
