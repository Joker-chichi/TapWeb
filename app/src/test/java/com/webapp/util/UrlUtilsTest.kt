package com.tapweb.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    // --- normalize ---

    @Test
    fun `normalize adds https when no scheme`() {
        assertEquals("https://github.com", UrlUtils.normalize("github.com"))
    }

    @Test
    fun `normalize keeps https unchanged`() {
        assertEquals("https://github.com", UrlUtils.normalize("https://github.com"))
    }

    @Test
    fun `normalize keeps http unchanged`() {
        assertEquals("http://example.com", UrlUtils.normalize("http://example.com"))
    }

    @Test
    fun `normalize trims whitespace`() {
        assertEquals("https://github.com", UrlUtils.normalize("  github.com  "))
    }

    @Test
    fun `normalize returns empty for blank input`() {
        assertEquals("", UrlUtils.normalize(""))
        assertEquals("", UrlUtils.normalize("   "))
    }

    // --- isValid ---

    @Test
    fun `isValid returns true for valid https url`() {
        assertTrue(UrlUtils.isValid("https://github.com"))
    }

    @Test
    fun `isValid returns true for valid http url`() {
        assertTrue(UrlUtils.isValid("http://example.com"))
    }

    @Test
    fun `isValid returns true for url with path`() {
        assertTrue(UrlUtils.isValid("https://github.com/user/repo"))
    }

    @Test
    fun `isValid returns false for empty string`() {
        assertFalse(UrlUtils.isValid(""))
    }

    @Test
    fun `isValid returns false for string without dot`() {
        assertFalse(UrlUtils.isValid("https://localhost"))
    }

    // --- extractDomain ---

    @Test
    fun `extractDomain returns host from url`() {
        assertEquals("github.com", UrlUtils.extractDomain("https://github.com/user/repo"))
    }

    @Test
    fun `extractDomain returns host from http url`() {
        assertEquals("example.com", UrlUtils.extractDomain("http://example.com/path"))
    }

    @Test
    fun `extractDomain falls back to input on bad url`() {
        assertEquals("not-a-url", UrlUtils.extractDomain("not-a-url"))
    }
}
