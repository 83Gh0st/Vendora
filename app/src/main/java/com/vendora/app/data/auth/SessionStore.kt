package com.vendora.app.data.auth

import android.content.Context

/** A signed-in session — everything the app needs to remember between launches. */
data class LocalSession(
    val token: String,
    val shopId: String,
    val role: String,
    val phoneDigits: String
)

/**
 * Persists the custom session (see supabase/schema.sql's shop_sessions
 * table) locally. This replaces what Supabase Auth's SDK would normally
 * manage automatically, since this app doesn't use Supabase Auth at all.
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("vendora_session", Context.MODE_PRIVATE)

    fun save(session: LocalSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_SHOP_ID, session.shopId)
            .putString(KEY_ROLE, session.role)
            .putString(KEY_PHONE, session.phoneDigits)
            .apply()
    }

    fun load(): LocalSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val shopId = prefs.getString(KEY_SHOP_ID, null) ?: return null
        val role = prefs.getString(KEY_ROLE, null) ?: return null
        val phone = prefs.getString(KEY_PHONE, null) ?: return null
        return LocalSession(token, shopId, role, phone)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_SHOP_ID = "shop_id"
        private const val KEY_ROLE = "role"
        private const val KEY_PHONE = "phone"
    }
}
