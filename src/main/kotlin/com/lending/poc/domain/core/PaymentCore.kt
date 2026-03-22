package com.lending.poc.domain.core

import com.lending.poc.domain.model.*
import java.time.LocalDate

/**
 * Pure payment allocation functions — no I/O, fully deterministic.
 *
 * Allocation order: outstanding fees → outstanding interest → outstanding principal
 */
@org.springframework.stereotype.Component
class PaymentCore {

    data class PaymentAllocationResult(
        val updatedLoan: Loan,
        val ledgerEntries: List<LedgerEntry>,
        val remainingAmount: Money,
    )

    /**
     * Allocates a payment to a loan following the priority: fees → interest → principal.
     *
     * @param loan the current loan state
     * @param paymentAmount the amount being paid
     * @param asOf the date of the payment
     * @return allocation result with updated loan, generated ledger entries, and any remaining amount
     */
    fun allocatePayment(
        loan: Loan,
        paymentAmount: Money,
        asOf: LocalDate,
    ): PaymentAllocationResult {
        require(paymentAmount.isPositive()) { "Payment amount must be positive" }

        var remaining = paymentAmount
        var updatedLoan = loan
        val entries = mutableListOf<LedgerEntry>()

        // 1. Apply to outstanding fees
        if (updatedLoan.outstandingFees.isPositive() && remaining.isPositive()) {
            val feePayment = minOf(remaining, updatedLoan.outstandingFees)
            val newFees = updatedLoan.outstandingFees - feePayment
            updatedLoan = updatedLoan.copy(outstandingFees = newFees)
            remaining = remaining - feePayment
            entries += LedgerEntry(
                loanId = loan.id,
                entryType = LedgerEntryType.PAYMENT,
                amount = feePayment,
                runningBalance = updatedLoan.totalOutstanding(),
                description = "Payment applied to fees",
                entryDate = asOf,
            )
        }

        // 2. Apply to outstanding interest
        if (updatedLoan.outstandingInterest.isPositive() && remaining.isPositive()) {
            val interestPayment = minOf(remaining, updatedLoan.outstandingInterest)
            val newInterest = updatedLoan.outstandingInterest - interestPayment
            updatedLoan = updatedLoan.copy(outstandingInterest = newInterest)
            remaining = remaining - interestPayment
            entries += LedgerEntry(
                loanId = loan.id,
                entryType = LedgerEntryType.PAYMENT,
                amount = interestPayment,
                runningBalance = updatedLoan.totalOutstanding(),
                description = "Payment applied to interest",
                entryDate = asOf,
            )
        }

        // 3. Apply to outstanding principal
        if (updatedLoan.outstandingPrincipal.isPositive() && remaining.isPositive()) {
            val principalPayment = minOf(remaining, updatedLoan.outstandingPrincipal)
            val newPrincipal = updatedLoan.outstandingPrincipal - principalPayment
            updatedLoan = updatedLoan.copy(outstandingPrincipal = newPrincipal)
            remaining = remaining - principalPayment
            entries += LedgerEntry(
                loanId = loan.id,
                entryType = LedgerEntryType.PAYMENT,
                amount = principalPayment,
                runningBalance = updatedLoan.totalOutstanding(),
                description = "Payment applied to principal",
                entryDate = asOf,
            )
        }

        return PaymentAllocationResult(updatedLoan, entries, remaining)
    }

    /** Returns the minimum of two Money values (same currency required). */
    private fun minOf(a: Money, b: Money): Money = if (a <= b) a else b

    /** Total outstanding balance: principal + interest + fees */
    private fun Loan.totalOutstanding(): Money =
        outstandingPrincipal + outstandingInterest + outstandingFees
}
