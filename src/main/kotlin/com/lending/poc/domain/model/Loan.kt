package com.lending.poc.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Loan(
    val id: UUID = UUID.randomUUID(),
    val borrowerId: String,
    val productId: String,
    val status: LoanStatus,
    val approvedAmount: Money,
    val outstandingPrincipal: Money,
    val outstandingInterest: Money,
    val outstandingFees: Money,
    val disbursedAmount: Money,
    val numberOfWithdrawals: Int,
    val originationDate: LocalDate?,
    val disbursementDate: LocalDate?,
    val nextPaymentDueDate: LocalDate?,
    val payoffDate: LocalDate?,
    val cooldownUntil: LocalDate?,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
