package com.taxiapp.driver.network

import com.google.gson.annotations.SerializedName

data class WalletTransactionDto(
    val id: Long,
    val amount: Double,
    val operationType: String, // DEPOSIT, COMMISSION, etc.
    val description: String?,
    val createdAt: String,
    val balanceAfter: Double, // ДОБАВЛЕНО: Залишок после операции
    val orderId: Long?        // ДОБАВЛЕНО: ID заказа для открытия деталей истории
)

// Дополнительные DTO для карт водителя
data class DriverCardDto(
    val id: Long,
    val cardNumber: String,
    val cardHolder: String?,
    val isMain: Boolean
)

data class AddCardRequest(
    val cardNumber: String,
    val cardHolder: String?
)