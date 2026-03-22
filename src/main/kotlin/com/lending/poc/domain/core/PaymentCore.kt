package com.lending.poc.domain.core

import com.lending.poc.domain.model.*
import com.lending.poc.domain.product.PaymentHierarchyConfig
import com.lending.poc.domain.product.PaymentSlotConfig
import org.springframework.stereotype.Component
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Pure payment allocation functions — no I/O, fully deterministic.
 *
 * Payment is distributed across outstanding balances following the product-configured
 * [PaymentHierarchyConfig].  Each [PaymentSlotConfig] defines one "waterfall step":
 * - If [PaymentSlotConfig.companionBucket] is absent, the full available amount is applied
 *   to the primary bucket.
 * - If a companion bucket is present, the allocation between the two is controlled by
 *   [PaymentSlotConfig.strategy]: SEQUENTIAL (primary first, then companion) or PROPORTIONAL
 *   (split in proportion to each bucket's outstanding balance).
 */
@Component
class PaymentCore {

    data class PaymentAllocationResult(
        val updatedLoan: Loan,
        val ledgerEntries: List<LedgerEntry>,
        val remainingAmount: Money,
    )

    /**
     * Allocates a payment to a loan following the product's [PaymentHierarchyConfig].
     *
     * @param loan          Current loan state.
     * @param paymentAmount Amount being paid.
     * @param asOf          Date of the payment.
     * @param hierarchy     Payment waterfall configuration (defaults to FEE → INTEREST → PRINCIPAL).
     */
    fun allocatePayment(
        loan: Loan,
        paymentAmount: Money,
        asOf: LocalDate,
        hierarchy: PaymentHierarchyConfig = PaymentHierarchyConfig.DEFAULT,
    ): PaymentAllocationResult {
        require(paymentAmount.isPositive()) { "Payment amount must be positive" }

        var remaining = paymentAmount
        var updatedLoan = loan
        val entries = mutableListOf<LedgerEntry>()

        for (slot in hierarchy.slots) {
            if (remaining.isZero()) break
            val primaryBalance = updatedLoan.getBalance(slot.bucket)
            val companionBalance = slot.companionBucket?.let { updatedLoan.getBalance(it) } ?: Money.ZERO

            if (primaryBalance.isZero() && companionBalance.isZero()) continue

            when {
                slot.companionBucket == null -> {
                    val payment = moneyMin(remaining, primaryBalance)
                    if (payment.isPositive()) {
                        updatedLoan = updatedLoan.withBalance(slot.bucket, primaryBalance - payment)
                        remaining -= payment
                        entries += ledgerEntry(loan, slot.bucket, payment, updatedLoan, asOf)
                    }
                }

                slot.strategy == AllocationStrategy.SEQUENTIAL -> {
                    val primaryPayment = moneyMin(remaining, primaryBalance)
                    if (primaryPayment.isPositive()) {
                        updatedLoan = updatedLoan.withBalance(slot.bucket, primaryBalance - primaryPayment)
                        remaining -= primaryPayment
                        entries += ledgerEntry(loan, slot.bucket, primaryPayment, updatedLoan, asOf)
                    }
                    val companionPayment = moneyMin(remaining, companionBalance)
                    if (companionPayment.isPositive()) {
                        updatedLoan = updatedLoan.withBalance(slot.companionBucket, companionBalance - companionPayment)
                        remaining -= companionPayment
                        entries += ledgerEntry(loan, slot.companionBucket, companionPayment, updatedLoan, asOf)
                    }
                }

                else -> {
                    // PROPORTIONAL: split available payment in proportion to outstanding balances
                    val total = primaryBalance + companionBalance
                    val available = moneyMin(remaining, total)
                    val primaryRatio = primaryBalance.amount.divide(total.amount, 10, RoundingMode.HALF_UP)
                    val primaryPayment = Money(
                        (available.amount * primaryRatio).setScale(2, RoundingMode.HALF_UP),
                        available.currency,
                    )
                    val companionPayment = available - primaryPayment

                    if (primaryPayment.isPositive()) {
                        updatedLoan = updatedLoan.withBalance(slot.bucket, primaryBalance - primaryPayment)
                        remaining -= primaryPayment
                        entries += ledgerEntry(loan, slot.bucket, primaryPayment, updatedLoan, asOf)
                    }
                    if (companionPayment.isPositive()) {
                        updatedLoan = updatedLoan.withBalance(slot.companionBucket, companionBalance - companionPayment)
                        remaining -= companionPayment
                        entries += ledgerEntry(loan, slot.companionBucket, companionPayment, updatedLoan, asOf)
                    }
                }
            }
        }

        return PaymentAllocationResult(updatedLoan, entries, remaining)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun moneyMin(a: Money, b: Money): Money = if (a <= b) a else b

    private fun Loan.totalOutstanding(): Money =
        outstandingPrincipal + outstandingInterest + outstandingFees + outstandingTaxOnInterest

    private fun Loan.getBalance(bucket: AllocationBucket): Money = when (bucket) {
        AllocationBucket.FEE -> outstandingFees
        AllocationBucket.INTEREST -> outstandingInterest
        AllocationBucket.TAX_ON_INTEREST -> outstandingTaxOnInterest
        AllocationBucket.PRINCIPAL -> outstandingPrincipal
    }

    private fun Loan.withBalance(bucket: AllocationBucket, newBalance: Money): Loan = when (bucket) {
        AllocationBucket.FEE -> copy(outstandingFees = newBalance)
        AllocationBucket.INTEREST -> copy(outstandingInterest = newBalance)
        AllocationBucket.TAX_ON_INTEREST -> copy(outstandingTaxOnInterest = newBalance)
        AllocationBucket.PRINCIPAL -> copy(outstandingPrincipal = newBalance)
    }

    private fun bucketDescription(bucket: AllocationBucket): String = when (bucket) {
        AllocationBucket.FEE -> "Payment applied to fees"
        AllocationBucket.INTEREST -> "Payment applied to interest"
        AllocationBucket.TAX_ON_INTEREST -> "Payment applied to tax on interest"
        AllocationBucket.PRINCIPAL -> "Payment applied to principal"
    }

    private fun ledgerEntry(
        originalLoan: Loan,
        bucket: AllocationBucket,
        amount: Money,
        updatedLoan: Loan,
        asOf: LocalDate,
    ) = LedgerEntry(
        loanId = originalLoan.id,
        entryType = LedgerEntryType.PAYMENT,
        amount = amount,
        runningBalance = updatedLoan.totalOutstanding(),
        description = bucketDescription(bucket),
        entryDate = asOf,
    )
}

