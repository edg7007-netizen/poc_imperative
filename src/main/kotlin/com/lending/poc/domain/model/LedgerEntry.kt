package com.lending.poc.domain.model

import java.time.LocalDate
import java.util.UUID

data class LedgerEntry(
    val id: UUID = UUID.randomUUID(),
    val loanId: UUID,
    val entryType: LedgerEntryType,
    val amount: Money,
    val runningBalance: Money,
    val description: String,
    val entryDate: LocalDate,
)
