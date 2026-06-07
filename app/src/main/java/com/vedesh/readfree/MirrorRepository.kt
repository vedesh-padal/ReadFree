package com.vedesh.readfree

import android.content.Context

/**
 * Owns all mirror-related state and persistence.
 *
 * Responsibilities:
 *  - Holds the ordered list of built-in fallback mirrors
 *  - Persists the user's preferred mirror via SharedPreferences
 *  - Builds the full proxy URL for a given article URL
 *  - Tracks which mirror is currently active during failover
 *
 * MainActivity depends on this class for all mirror decisions so that none
 * of that logic leaks into the UI layer.
 */
class MirrorRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Built-in mirrors tried in order during automatic failover. */
    val builtInMirrors = listOf(
        "https://freedium.cfd/",
        "https://www.freedium.cfd/",
        "https://scribe.rip/"
    )

    /** Index into [builtInMirrors] used during failover. Reset to 0 on each new article load. */
    var currentMirrorIndex: Int = 0
        private set

    // ── Persistence ──────────────────────────────────────────────────────────

    /**
     * Returns the active mirror base URL.
     * User-saved preference takes priority; falls back to [builtInMirrors][0].
     */
    fun getActiveMirror(): String {
        return prefs.getString(PREF_MIRROR_URL, builtInMirrors[0]) ?: builtInMirrors[0]
    }

    /** Persists [url] as the user's preferred mirror. */
    fun saveUserMirror(url: String) {
        prefs.edit().putString(PREF_MIRROR_URL, url).apply()
    }

    // ── Failover ─────────────────────────────────────────────────────────────

    /** Resets the failover index. Call this at the start of every new article load. */
    fun resetMirrorIndex() {
        currentMirrorIndex = 0
    }

    /**
     * Advances to the next built-in mirror.
     * Returns true if a next mirror is available, false if all are exhausted.
     */
    fun tryNextMirror(): Boolean {
        val next = currentMirrorIndex + 1
        return if (next < builtInMirrors.size) {
            currentMirrorIndex = next
            true
        } else {
            false
        }
    }

    // ── URL building ─────────────────────────────────────────────────────────

    /**
     * Builds the full proxy URL for [originalUrl].
     *
     * Rules:
     *  - If [originalUrl] already starts with a known mirror URL, return it as-is.
     *  - On the first attempt (index 0), use the user's preferred mirror.
     *  - On subsequent failover attempts, use [builtInMirrors][currentMirrorIndex].
     */
    fun buildProxyUrl(originalUrl: String): String {
        if (builtInMirrors.any { originalUrl.startsWith(it) }) return originalUrl
        val base = if (currentMirrorIndex == 0) getActiveMirror() else builtInMirrors[currentMirrorIndex]
        return "$base$originalUrl"
    }

    /**
     * Returns the URL for the current failover mirror without saving state.
     * Used by [tryNextMirrorOrShowError] after [tryNextMirror] has advanced the index.
     */
    fun currentMirrorUrl(articleUrl: String): String {
        return builtInMirrors[currentMirrorIndex] + articleUrl
    }

    companion object {
        private const val PREFS_NAME = "readfree_prefs"
        private const val PREF_MIRROR_URL = "mirror_url"
    }
}
