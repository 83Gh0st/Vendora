package com.vendora.app.data

import android.content.Context

/**
 * Tracks whether this device has already shown the "How Vendora Works"
 * walkthrough for a given shop, so it appears automatically the very first
 * time someone reaches the main app (right after registering a new shop or
 * joining one as staff) without repeating itself on every later sign-in.
 *
 * Keyed by shopId rather than a single global flag, so if this device is
 * ever signed into a different shop later, that shop still gets its own
 * first-run walkthrough. The on-demand replay from Tools doesn't depend on
 * this at all — it always shows, regardless of what's stored here.
 */
class TutorialPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("vendora_tutorial", Context.MODE_PRIVATE)

    fun hasSeenTutorial(shopId: String): Boolean = prefs.getBoolean(key(shopId), false)

    fun markTutorialSeen(shopId: String) {
        prefs.edit().putBoolean(key(shopId), true).apply()
    }

    private fun key(shopId: String) = "seen_$shopId"
}
