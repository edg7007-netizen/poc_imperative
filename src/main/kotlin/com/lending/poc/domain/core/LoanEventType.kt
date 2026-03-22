package com.lending.poc.domain.core

/**
 * Strongly-typed domain event types. Replaces raw String event type in [LoanEffect.EmitEvent].
 */
enum class LoanEventType {
    LOAN_APPROVED,
    LOAN_DISBURSED,
    LOAN_CANCELLED,
    LOAN_PAYMENT_RECEIVED,
    LOAN_PAID_OFF,
    LOAN_WITHDRAWAL_MADE,
    INTEREST_ACCRUED,
    LATE_FEE_ACCRUED,
}
