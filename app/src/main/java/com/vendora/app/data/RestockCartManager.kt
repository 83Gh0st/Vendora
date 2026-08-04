package com.vendora.app.data

import androidx.compose.runtime.mutableStateOf

object RestockCartManager {
    // Maps Product ID to the order quantity string (e.g. "50 Pieces")
    val cart = mutableStateOf<Map<Int, String>>(emptyMap())

    fun addToCart(productId: Int, quantityStr: String) {
        val currentCart = cart.value.toMutableMap()
        currentCart[productId] = quantityStr
        cart.value = currentCart
    }

    fun clearCart() {
        cart.value = emptyMap()
    }
}
