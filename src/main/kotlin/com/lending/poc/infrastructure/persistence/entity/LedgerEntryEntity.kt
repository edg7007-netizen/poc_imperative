package com.lending.poc.infrastructure.persistence.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "ledger_entries")
class LedgerEntryEntity(

    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "loan_id", nullable = false)
    val loanId: UUID = UUID.randomUUID(),

    @Column(name = "entry_type", nullable = false)
    val entryType: String = "",

    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "running_balance", nullable = false, precision = 19, scale = 2)
    val runningBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String = "USD",

    @Column(nullable = false)
    val description: String = "",

    @Column(name = "entry_date", nullable = false)
    val entryDate: LocalDate = LocalDate.now(),
)
