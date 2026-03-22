package com.lending.poc.domain.model

/**
 * Identifies which outstanding balance on a [Loan] a payment slot targets.
 */
enum class AllocationBucket {
    /** Outstanding origination, late, draw, or NSF fees. */
    FEE,
    /** Outstanding accrued interest. */
    INTEREST,
    /** Outstanding tax computed on accrued interest (e.g., withholding tax). */
    TAX_ON_INTEREST,
    /** Outstanding principal. */
    PRINCIPAL,
}
