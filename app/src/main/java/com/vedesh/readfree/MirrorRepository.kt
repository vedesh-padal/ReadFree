package com.vedesh.readfree

import android.content.Context

/**
 * Owns all mirror-related state and persistence.
 *
 * Responsibilities:
 *  - Holds the default fallback mirror (freedium-mirror.cfd)
 *  - Persists the user's preferred mirror via SharedPreferences
 *  - Builds the full proxy URL for a given article URL
 *  - Tracks which mirror is currently active during failover
 */
class MirrorRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        val current = prefs.getString(PREF_MIRROR_URL, null)
        val deprecatedMirrors = setOf(
            "https://freedium.cfd/",
        )
        if (current != null && current in deprecatedMirrors) {
            prefs.edit().putString(PREF_MIRROR_URL, DEFAULT_MIRROR).apply()
        }
    }

    // Dynamic list of mirrors for the current load attempt.
    // Built inside resetMirrorIndex() based on active mirror and default.
    private var activeTryList = listOf(DEFAULT_MIRROR)

    /** Index into [activeTryList] used during failover. Reset to 0 on each new article load. */
    var currentMirrorIndex: Int = 0
        private set

    // ── Persistence ──────────────────────────────────────────────────────────

    /**
     * Returns the active mirror base URL.
     * User-saved preference takes priority; falls back to [DEFAULT_MIRROR].
     */
    fun getActiveMirror(): String {
        return prefs.getString(PREF_MIRROR_URL, DEFAULT_MIRROR) ?: DEFAULT_MIRROR
    }

    /** Persists [url] as the user's preferred mirror. */
    fun saveUserMirror(url: String) {
        prefs.edit().putString(PREF_MIRROR_URL, url).apply()
    }

    // ── Failover ─────────────────────────────────────────────────────────────

    /** Resets the failover index. Call this at the start of every new article load. */
    fun resetMirrorIndex() {
        currentMirrorIndex = 0
        val active = getActiveMirror()
        val list = mutableListOf(active)
        if (active != DEFAULT_MIRROR) {
            list.add(DEFAULT_MIRROR)
        }
        activeTryList = list
    }

    /**
     * Advances to the next mirror in the active try list.
     * Returns true if a next mirror is available, false if all are exhausted.
     */
    fun tryNextMirror(): Boolean {
        val next = currentMirrorIndex + 1
        return if (next < activeTryList.size) {
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
     *  - Use [activeTryList][currentMirrorIndex] to proxy the url.
     */
    fun buildProxyUrl(originalUrl: String): String {
        if (originalUrl.startsWith(DEFAULT_MIRROR) || originalUrl.startsWith(getActiveMirror())) {
            return originalUrl
        }
        return "${activeTryList[currentMirrorIndex]}$originalUrl"
    }

    /**
     * Returns the URL for the current failover mirror without saving state.
     * Used by [tryNextMirrorOrShowError] after [tryNextMirror] has advanced the index.
     */
    fun currentMirrorUrl(articleUrl: String): String {
        return activeTryList[currentMirrorIndex] + articleUrl
    }

    companion object {
        private const val PREFS_NAME = "readfree_prefs"
        private const val PREF_MIRROR_URL = "mirror_url"
        const val DEFAULT_MIRROR = "https://freedium-mirror.cfd/"
    }
}
