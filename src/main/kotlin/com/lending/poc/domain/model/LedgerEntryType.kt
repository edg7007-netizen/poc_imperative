package com.lending.poc.domain.model

enum class LedgerEntryType {
    DISBURSEMENT,
    PAYMENT,
    INTEREST_ACCRUAL,
    FEE_ORIGINATION,
    FEE_LATE,
    FEE_DRAW,
    FEE_NSF,
}
