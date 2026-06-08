package com.vedesh.readfree

import android.net.Uri

/**
 * Pure utility functions for URL handling.
 * No Android lifecycle or UI state — safe to call from anywhere.
 */
object UrlUtils {

    private val KNOWN_MEDIUM_HOSTS = setOf(
        "medium.com",
        "towardsdatascience.com",
        "betterprogramming.pub",
        "levelup.gitconnected.com",
        "javascript.plainenglish.io",
        "itnext.io",
        "blog.bitsrc.io",
        "hackernoon.com"
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
     * Every Medium article URL ends with `/<slug>-<hexId>`, where hexId is the
     * article's unique 12-character hex identifier (e.g.
     * `...performance-830999c13919`). This is true even on custom domains.
     */
    private val MEDIUM_ARTICLE_ID_PATTERN = Regex("-[a-f0-9]{12}\$", RegexOption.IGNORE_CASE)

    /**
     * Returns true if [url] belongs to a Medium publication — either a known
     * Medium host (medium.com, etc.) or a custom domain whose URL path matches
     * the Medium article pattern (/<slug>-<hexId>).
     *
     * Used to decide whether to proxy the URL through Freedium.
     */
    fun isMediumDomain(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return false

            // Fast path: known Medium hosts
            if (KNOWN_MEDIUM_HOSTS.any { host.endsWith(it) }) return true

            // Medium custom domains (e.g. towardsdev.com) don't match the host list,
            // but every Medium article path ends with /<slug>-<hexId>.
            val path = uri.path ?: return false
            val lastSegment = path.trimEnd('/').split('/').lastOrNull() ?: return false
            MEDIUM_ARTICLE_ID_PATTERN.containsMatchIn(lastSegment)
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
