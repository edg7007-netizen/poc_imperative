package com.lending.poc.web.dto

import java.math.BigDecimal

data class ApplyForLoanRequest(
    val productId: String,
    val borrowerId: String,
    val requestedAmount: BigDecimal,
    val currency: String = "USD",
    val pastLoanCount: Int = 0,
)

data class DisburseRequest(
    val amount: BigDecimal,
    val currency: String = "USD",
)

data class PaymentRequest(
    val amount: BigDecimal,
    val currency: String = "USD",
)

data class WithdrawalRequest(
    val amount: BigDecimal,
    val currency: String = "USD",
)
