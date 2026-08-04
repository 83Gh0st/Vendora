package com.vendora.app.data

import android.content.Context
import android.util.Log
import com.vendora.app.data.auth.SessionStore
import com.vendora.app.data.remote.RemoteProduct
import com.vendora.app.data.remote.RemoteSale
import com.vendora.app.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Keeps a shop's Room database backed up in Supabase, shared by every
 * member of that shop (owner + employees). Since this app doesn't use
 * Supabase Auth, every call goes through the get_products/replace_products/
 * get_sales/replace_sales Postgres functions (see supabase/schema.sql),
 * passing the locally-stored session token — those functions do their own
 * validation, so there's nothing to authenticate here beyond having a token.
 *
 * Sync strategy is intentionally simple ("replace the cloud copy with what's
 * on the device") rather than incremental/conflict-resolving — that matches
 * how the app is actually used and keeps the logic easy to reason about.
 */
class SupabaseSyncManager(context: Context) {

    private val appContext = context.applicationContext
    private val supabase = SupabaseClientProvider.client
    private val sessionStore = SessionStore(appContext)

    /** Push everything currently in Room up to Supabase. Call after any local write. */
    suspend fun pushAll() {
        if (!SupabaseClientProvider.isConfigured) return
        val token = sessionStore.load()?.token ?: return

        try {
            val db = AppDatabase.getDatabase(appContext)
            val products = db.productDao().getAllProducts().first()
            val sales = db.saleDao().getAllSales().first()

            val productsJson = buildJsonArray {
                products.forEach { p ->
                    add(buildJsonObject {
                        put("localId", JsonPrimitive(p.id))
                        put("name", JsonPrimitive(p.name))
                        put("barcode", JsonPrimitive(p.barcode))
                        put("sellingPrice", JsonPrimitive(p.sellingPrice))
                        put("currentStock", JsonPrimitive(p.currentStock))
                        put("unit", JsonPrimitive(p.unit))
                    })
                }
            }
            supabase.postgrest.rpc(
                "replace_products",
                buildJsonObject {
                    put("p_token", JsonPrimitive(token))
                    put("p_products", productsJson)
                }
            )

            val salesJson = buildJsonArray {
                sales.forEach { s ->
                    add(buildJsonObject {
                        put("localId", JsonPrimitive(s.id))
                        put("timestamp", JsonPrimitive(s.timestamp))
                        put("totalAmount", JsonPrimitive(s.totalAmount))
                        put("itemsSummary", JsonPrimitive(s.itemsSummary))
                        put("cashAmount", JsonPrimitive(s.cashAmount))
                        put("upiAmount", JsonPrimitive(s.upiAmount))
                        put("isCredit", JsonPrimitive(s.isCredit))
                        put("customerName", JsonPrimitive(s.customerName))
                        put("customerPhone", JsonPrimitive(s.customerPhone))
                    })
                }
            }
            supabase.postgrest.rpc(
                "replace_sales",
                buildJsonObject {
                    put("p_token", JsonPrimitive(token))
                    put("p_sales", salesJson)
                }
            )
        } catch (e: Exception) {
            // Best-effort background sync: a failed push (offline, etc.)
            // should never crash the app or block the UI.
            Log.w("SupabaseSyncManager", "pushAll failed: ${e.message}")
        }
    }

    /**
     * On a fresh sign-in with an empty local database (e.g. a new device, or
     * an employee's first login), pull down whatever the shop has backed up.
     * Never overwrites existing local data, so it's safe to call on every login.
     */
    suspend fun pullIfLocalIsEmpty() {
        if (!SupabaseClientProvider.isConfigured) return
        val token = sessionStore.load()?.token ?: return

        try {
            val db = AppDatabase.getDatabase(appContext)
            val hasLocalProducts = db.productDao().getAllProducts().first().isNotEmpty()
            if (hasLocalProducts) return

            val remoteProducts = supabase.postgrest.rpc(
                "get_products",
                buildJsonObject { put("p_token", JsonPrimitive(token)) }
            ).decodeList<RemoteProduct>()

            val remoteSales = supabase.postgrest.rpc(
                "get_sales",
                buildJsonObject { put("p_token", JsonPrimitive(token)) }
            ).decodeList<RemoteSale>()

            remoteProducts.forEach { rp ->
                db.productDao().insertProduct(
                    ProductEntity(
                        id = rp.localId,
                        name = rp.name,
                        barcode = rp.barcode,
                        sellingPrice = rp.sellingPrice,
                        currentStock = rp.currentStock,
                        unit = rp.unit
                    )
                )
            }
            remoteSales.forEach { rs ->
                db.saleDao().insertSale(
                    SaleEntity(
                        id = rs.localId,
                        timestamp = rs.timestamp,
                        totalAmount = rs.totalAmount,
                        itemsSummary = rs.itemsSummary,
                        cashAmount = rs.cashAmount,
                        upiAmount = rs.upiAmount,
                        isCredit = rs.isCredit,
                        customerName = rs.customerName,
                        customerPhone = rs.customerPhone
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("SupabaseSyncManager", "pullIfLocalIsEmpty failed: ${e.message}")
        }
    }
}
