package com.lending.poc.infrastructure.persistence.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "loans")
class LoanEntity(

    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "borrower_id", nullable = false)
    val borrowerId: String = "",

    @Column(name = "product_id", nullable = false)
    val productId: String = "",

    @Column(nullable = false)
    var status: String = "",

    @Column(name = "approved_amount", nullable = false, precision = 19, scale = 2)
    var approvedAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "outstanding_principal", nullable = false, precision = 19, scale = 2)
    var outstandingPrincipal: BigDecimal = BigDecimal.ZERO,

    @Column(name = "outstanding_interest", nullable = false, precision = 19, scale = 2)
    var outstandingInterest: BigDecimal = BigDecimal.ZERO,

    @Column(name = "outstanding_fees", nullable = false, precision = 19, scale = 2)
    var outstandingFees: BigDecimal = BigDecimal.ZERO,

    @Column(name = "disbursed_amount", nullable = false, precision = 19, scale = 2)
    var disbursedAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "number_of_withdrawals", nullable = false)
    var numberOfWithdrawals: Int = 0,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String = "USD",

    @Column(name = "origination_date")
    var originationDate: LocalDate? = null,

    @Column(name = "disbursement_date")
    var disbursementDate: LocalDate? = null,

    @Column(name = "next_payment_due_date")
    var nextPaymentDueDate: LocalDate? = null,

    @Column(name = "payoff_date")
    var payoffDate: LocalDate? = null,

    @Column(name = "cooldown_until")
    var cooldownUntil: LocalDate? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
