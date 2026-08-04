package com.vendora.app.data.remote

import com.vendora.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Single shared Supabase client for the whole app.
 *
 * The URL/key are pulled from BuildConfig, which in turn is populated from
 * local.properties at build time (see app/build.gradle) — they are never
 * hardcoded here.
 *
 * Deliberately no Auth plugin here: Vendora doesn't use Supabase Auth at
 * all (see AuthViewModel + supabase/schema.sql) — phone number + password
 * sign-in is handled entirely through custom Postgres functions instead.
 * [Postgrest] gives us the RPC calls those functions need, and [Storage]
 * handles verification photo/document uploads.
 */
object SupabaseClientProvider {

    @OptIn(ExperimentalSerializationApi::class)
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            // Every Postgres function in supabase/schema.sql returns plain
            // snake_case columns (session_token, shop_id, selling_price, ...)
            // since that's Postgres' native column-naming convention, while
            // every @Serializable result class on the Kotlin side is
            // camelCase (sessionToken, shopId, sellingPrice, ...) to match
            // Kotlin style. Without this, decodeList<...>() can't match the
            // JSON keys it gets back to the model's properties and throws
            // "Fields [...] are required ... but they were missing" — this
            // makes the (de)serializer convert automatically instead of
            // requiring a @SerialName on every single field.
            defaultSerializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true
                    namingStrategy = JsonNamingStrategy.SnakeCase
                }
            )
            install(Postgrest)
            install(Storage)
        }
    }

    /** True once local.properties actually has real Supabase credentials in it. */
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
}
