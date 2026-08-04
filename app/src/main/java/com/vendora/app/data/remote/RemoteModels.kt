package com.vendora.app.data.remote

import kotlinx.serialization.Serializable

/** A product as stored/synced with the shop's cloud backup. */
@Serializable
data class RemoteProduct(
    val localId: Int,
    val name: String,
    val barcode: String,
    val sellingPrice: Double,
    val currentStock: Int,
    val unit: String
)

/** A sale as stored/synced with the shop's cloud backup. */
@Serializable
data class RemoteSale(
    val localId: Int,
    val timestamp: Long,
    val totalAmount: Double,
    val itemsSummary: String,
    val cashAmount: Double = 0.0,
    val upiAmount: Double = 0.0,
    val isCredit: Boolean = false,
    val customerName: String = "",
    val customerPhone: String = ""
)

/** Decoded result of the register_shop() Postgres function. */
@Serializable
data class RegisterShopResult(
    val sessionToken: String,
    val shopId: String
)

/** Decoded result of the join_shop_with_code() Postgres function. */
@Serializable
data class JoinShopResult(
    val sessionToken: String,
    val shopId: String
)

/** Decoded result of the login_shop_account() Postgres function. */
@Serializable
data class LoginResult(
    val sessionToken: String,
    val shopId: String,
    val role: String
)

/** Decoded result of the get_shop_details() Postgres function. */
@Serializable
data class ShopDetailsResult(
    val shopId: String,
    val shopName: String,
    val address: String,
    val joinCode: String,
    val verificationStatus: String,
    val role: String
)
