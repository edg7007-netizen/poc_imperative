package com.lending.poc.domain.model

import java.time.LocalDate

/**
 * One row in a loan amortization schedule.
 *
 * @param period             Payment number (1 = first payment).
 * @param paymentDate        Scheduled date of this payment.
 * @param openingBalance     Outstanding principal at the start of this period.
 * @param scheduledPayment   Total payment amount due.
 * @param principalComponent Amount applied to reduce the principal.
 * @param interestComponent  Amount covering interest for this period.
 * @param closingBalance     Outstanding principal after this payment.
 */
data class AmortizationRow(
    val period: Int,
    val paymentDate: LocalDate,
    val openingBalance: Money,
    val scheduledPayment: Money,
    val principalComponent: Money,
    val interestComponent: Money,
    val closingBalance: Money,
)
