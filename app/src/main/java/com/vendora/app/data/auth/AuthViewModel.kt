package com.vendora.app.data.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vendora.app.data.remote.JoinShopResult
import com.vendora.app.data.remote.LoginResult
import com.vendora.app.data.remote.RegisterShopResult
import com.vendora.app.data.remote.ShopDetailsResult
import com.vendora.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Vendora's entire sign-in system: phone number + password, fully custom,
 * with no Supabase Auth involved at all. Supabase's dashboard won't let
 * you enable its Phone provider without paid SMS-gateway credentials even
 * with confirmation disabled, so instead this talks directly to a handful
 * of Postgres functions (see supabase/schema.sql) that handle hashing,
 * session tokens, and shop membership themselves. [SessionStore] persists
 * the resulting token locally, standing in for what Supabase Auth's SDK
 * would normally manage.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val supabase = SupabaseClientProvider.client
    private val sessionStore = SessionStore(application)

    private val _session = MutableStateFlow(sessionStore.load())
    val session: StateFlow<LocalSession?> = _session.asStateFlow()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Owner path: upload the verification photo, then create the shop + account atomically. */
    fun register(
        phoneDigits: String,
        password: String,
        shopName: String,
        ownerName: String,
        address: String,
        gstNumber: String,
        verificationMethod: String,
        verificationImageBytes: ByteArray,
        fileExtension: String
    ) {
        val validation = validateCredentials(phoneDigits, password)
            ?: validateShopDetails(shopName, ownerName, address)
        if (validation != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validation)
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val path = "unregistered/${UUID.randomUUID()}.$fileExtension"
                supabase.storage.from("shop-verification").upload(path, verificationImageBytes)

                val params = buildJsonObject {
                    put("p_phone", JsonPrimitive(phoneDigits))
                    put("p_password", JsonPrimitive(password))
                    put("p_shop_name", JsonPrimitive(shopName.trim()))
                    put("p_owner_name", JsonPrimitive(ownerName.trim()))
                    put("p_address", JsonPrimitive(address.trim()))
                    put("p_gst_number", JsonPrimitive(gstNumber.trim()))
                    put("p_verification_method", JsonPrimitive(verificationMethod))
                    put("p_verification_file_path", JsonPrimitive(path))
                }
                val result = supabase.postgrest.rpc("register_shop", params).decodeList<RegisterShopResult>().first()

                val newSession = LocalSession(result.sessionToken, result.shopId, "owner", phoneDigits)
                sessionStore.save(newSession)
                _session.value = newSession
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = friendlyMessage(e))
            }
        }
    }

    /** Employee path: phone + password + the owner's shop code. */
    fun joinShop(phoneDigits: String, password: String, joinCode: String) {
        val validation = validateCredentials(phoneDigits, password) ?: run {
            if (joinCode.isBlank()) "Enter the shop code your owner shared with you" else null
        }
        if (validation != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validation)
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("p_phone", JsonPrimitive(phoneDigits))
                    put("p_password", JsonPrimitive(password))
                    put("p_join_code", JsonPrimitive(joinCode.trim()))
                }
                val result = supabase.postgrest.rpc("join_shop_with_code", params).decodeList<JoinShopResult>().first()

                val newSession = LocalSession(result.sessionToken, result.shopId, "employee", phoneDigits)
                sessionStore.save(newSession)
                _session.value = newSession
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = friendlyMessage(e))
            }
        }
    }

    fun signIn(phoneDigits: String, password: String) {
        val validation = validateCredentials(phoneDigits, password)
        if (validation != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validation)
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("p_phone", JsonPrimitive(phoneDigits))
                    put("p_password", JsonPrimitive(password))
                }
                val result = supabase.postgrest.rpc("login_shop_account", params).decodeList<LoginResult>().first()

                val newSession = LocalSession(result.sessionToken, result.shopId, result.role, phoneDigits)
                sessionStore.save(newSession)
                _session.value = newSession
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = friendlyMessage(e))
            }
        }
    }

    fun signOut() {
        val token = _session.value?.token
        sessionStore.clear()
        _session.value = null
        viewModelScope.launch {
            // Wipe the locally-cached products/sales along with the session —
            // otherwise, since SupabaseSyncManager only pulls from the cloud
            // when the local cache is empty, a different shop signing in on
            // this same device later would see this shop's leftover data
            // until it happened to add its own first product.
            withContext(Dispatchers.IO) {
                com.vendora.app.data.AppDatabase.getDatabase(getApplication()).clearAllTables()
            }
            if (token != null) {
                try {
                    supabase.postgrest.rpc("logout_session", buildJsonObject { put("p_token", JsonPrimitive(token)) })
                } catch (_: Exception) {
                    // Best-effort — the local session is already cleared either way.
                }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /** For the Settings screen: shop name, address, join code, verification status. */
    suspend fun fetchShopDetails(): ShopDetailsResult? {
        val token = _session.value?.token ?: return null
        return try {
            supabase.postgrest.rpc("get_shop_details", buildJsonObject { put("p_token", JsonPrimitive(token)) })
                .decodeList<ShopDetailsResult>()
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun validateCredentials(phoneDigits: String, password: String): String? = when {
        phoneDigits.length != 10 -> "Enter a valid 10-digit mobile number"
        password.length < 6 -> "Password must be at least 6 characters"
        else -> null
    }

    private fun validateShopDetails(shopName: String, ownerName: String, address: String): String? = when {
        shopName.isBlank() -> "Enter your shop's name"
        ownerName.isBlank() -> "Enter the owner's name"
        address.isBlank() -> "Enter the shop's address"
        else -> null
    }

    private fun friendlyMessage(e: Exception): String =
        e.message?.takeIf { it.isNotBlank() }
            ?: "Couldn't reach the server. Check your connection and try again."
}
