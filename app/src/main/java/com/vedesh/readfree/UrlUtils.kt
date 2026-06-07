package com.vedesh.readfree

import android.net.Uri

/**
 * Pure utility functions for URL handling.
 * No Android lifecycle or UI state — safe to call from anywhere.
 */
object UrlUtils {
    private val KNOWN_MEDIUM_HOSTS =
        setOf(
            "medium.com",
            "towardsdatascience.com",
            "betterprogramming.pub",
            "levelup.gitconnected.com",
            "javascript.plainenglish.io",
            "itnext.io",
            "blog.bitsrc.io",
            "hackernoon.com",
        )

    /**
     * Extracts the first http/https URL found in [text].
     * Chrome and most apps share in the format "Title\nhttps://..." so we scan for the URL.
     */
    fun extractUrl(text: String): String? {
        val regex = Regex("https?://[^\\s]+")
        return regex.find(text)?.value
    }

    /**
     * Returns true if [url] belongs to a known Medium publication domain.
     * Used by the WebViewClient to decide whether to intercept navigation.
     */
    fun isMediumDomain(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host ?: return false
            KNOWN_MEDIUM_HOSTS.any { host.endsWith(it) }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Truncates [url] to fit inside the toolbar URL bar without wrapping.
     */
    fun formatUrlForDisplay(url: String): String {
        return if (url.length > 60) url.take(57) + "…" else url
    }
}
