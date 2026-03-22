package com.lending.poc.domain.core

import com.lending.poc.domain.model.*
import com.lending.poc.domain.product.LendingProduct
import com.lending.poc.domain.product.WithdrawalConfig
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The heart of the application: a pure functional state machine for loan lifecycle transitions.
 *
 * Every function:
 *  - Accepts current state + input → returns (newState, List<LoanEffect>)
 *  - Has ZERO side effects (no I/O, no randomness, no external calls)
 */
@Service
class LoanLifecycleCore(
    private val interestCore: InterestCore,
    private val feeCore: FeeCore,
    private val paymentCore: PaymentCore,
    private val cooldownCore: CooldownCore,
) {

    // ──────────────────────────────────────────────────────────────────────────
    // approveLoan
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new loan in APPROVED status.
     * Validates that the requested amount falls within product limits.
     */
    fun approveLoan(
        product: LendingProduct,
        borrowerId: BorrowerId,
        requestedAmount: Money,
        now: LocalDate,
        pastLoanCount: Int = 0,
    ): Pair<Loan, List<LoanEffect>> {
        require(requestedAmount >= product.withdrawalConfig.minimumAmount) {
            "Requested amount ${requestedAmount} is below minimum ${product.withdrawalConfig.minimumAmount}"
        }
        require(requestedAmount <= product.withdrawalConfig.maximumAmount) {
            "Requested amount ${requestedAmount} exceeds maximum ${product.withdrawalConfig.maximumAmount}"
        }

        val loan = Loan(
            borrowerId = borrowerId,
            productId = product.id,
            status = LoanStatus.APPROVED,
            approvedAmount = requestedAmount,
            outstandingPrincipal = Money.ZERO,
            outstandingInterest = Money.ZERO,
            outstandingFees = Money.ZERO,
            disbursedAmount = Money.ZERO,
            numberOfWithdrawals = 0,
            originationDate = now,
            disbursementDate = null,
            nextPaymentDueDate = null,
            payoffDate = null,
            cooldownUntil = null,
        )

        val effects = listOf(
            LoanEffect.PersistLoan(loan),
            LoanEffect.EmitEvent(
                eventType = LoanEventType.LOAN_APPROVED,
                aggregateId = loan.id,
                payload = mapOf(
                    "loanId" to loan.id.toString(),
                    "borrowerId" to borrowerId.value,
                    "productId" to product.id.value,
                    "approvedAmount" to requestedAmount.amount.toPlainString(),
                    "currency" to requestedAmount.currency,
                    "approvedDate" to now.toString(),
                ),
            ),
            LoanEffect.SendNotification(
                recipientId = borrowerId,
                message = "Your loan application for ${requestedAmount} has been approved.",
            ),
        )

        return Pair(loan, effects)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // disburseLoan
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Disburses an APPROVED loan, transitioning it to ACTIVE.
     * For SINGLE withdrawal products the full approved amount is disbursed.
     * For MULTIPLE withdrawal products the requested amount is drawn (up to the limit).
     */
    fun disburseLoan(
        loan: Loan,
        product: LendingProduct,
        amount: Money,
        now: LocalDate,
    ): Pair<Loan, List<LoanEffect>> {
        require(loan.status == LoanStatus.APPROVED) {
            "Loan ${loan.id} must be APPROVED to disburse, but is ${loan.status}"
        }
        require(amount.isPositive()) { "Disbursement amount must be positive" }
        require(amount <= loan.approvedAmount) {
            "Disbursement amount ${amount} exceeds approved amount ${loan.approvedAmount}"
        }

        val originationFee = feeCore.calculateOriginationFee(
            loan.approvedAmount,
            product.feeConfig.originationFee,
        )
        val drawFee = if (product.withdrawalConfig.type == WithdrawalType.MULTIPLE) {
            feeCore.calculateDrawFee(amount, product.withdrawalConfig.drawFee)
        } else Money.ZERO

        val totalFees = originationFee + drawFee

        val nextDueDate = calculateNextDueDate(now, product)

        val activeLoan = loan.copy(
            status = LoanStatus.ACTIVE,
            outstandingPrincipal = amount,
            outstandingInterest = Money.ZERO,
            outstandingFees = totalFees,
            disbursedAmount = amount,
            numberOfWithdrawals = 1,
            disbursementDate = now,
            nextPaymentDueDate = nextDueDate,
        )

        val runningBalance = activeLoan.outstandingPrincipal + activeLoan.outstandingFees

        val disbursementEntry = LedgerEntry(
            loanId = loan.id,
            entryType = LedgerEntryType.DISBURSEMENT,
            amount = amount,
            runningBalance = runningBalance,
            description = "Loan disbursement",
            entryDate = now,
        )

        val effects = mutableListOf<LoanEffect>(
            LoanEffect.PersistLoan(activeLoan),
            LoanEffect.PersistLedgerEntry(disbursementEntry),
        )

        if (originationFee.isPositive()) {
            val feeEntry = LedgerEntry(
                loanId = loan.id,
                entryType = LedgerEntryType.FEE_ORIGINATION,
                amount = originationFee,
                runningBalance = runningBalance,
                description = "Origination fee (${product.feeConfig.originationFee.multiply(BigDecimal.valueOf(100)).toPlainString()}%)",
                entryDate = now,
            )
            effects += LoanEffect.PersistLedgerEntry(feeEntry)
        }

        if (drawFee.isPositive()) {
            val feeEntry = LedgerEntry(
                loanId = loan.id,
                entryType = LedgerEntryType.FEE_DRAW,
                amount = drawFee,
                runningBalance = runningBalance,
                description = "Draw fee",
                entryDate = now,
            )
            effects += LoanEffect.PersistLedgerEntry(feeEntry)
        }

        effects += LoanEffect.EmitEvent(
            eventType = LoanEventType.LOAN_DISBURSED,
            aggregateId = loan.id,
            payload = mapOf(
                "loanId" to loan.id.toString(),
                "borrowerId" to loan.borrowerId.value,
                "amount" to amount.amount.toPlainString(),
                "currency" to amount.currency,
                "disbursementDate" to now.toString(),
            ),
        )
        effects += LoanEffect.SendNotification(
            recipientId = loan.borrowerId,
            message = "Your loan of ${amount} has been disbursed. First payment due: $nextDueDate.",
        )
        effects += LoanEffect.ScheduleAccrual(loan.id, now.plusDays(1))

        return Pair(activeLoan, effects)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // withdrawFromCreditLine (revolving)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Processes an additional withdrawal from an active revolving line of credit.
     */
    fun withdrawFromCreditLine(
        loan: Loan,
        product: LendingProduct,
        amount: Money,
        now: LocalDate,
    ): Pair<Loan, List<LoanEffect>> {
        require(loan.status == LoanStatus.ACTIVE) {
            "Loan ${loan.id} must be ACTIVE for a withdrawal, but is ${loan.status}"
        }
        require(product.withdrawalConfig.type == WithdrawalType.MULTIPLE) {
            "Product ${product.id} does not support multiple withdrawals"
        }
        require(amount.isPositive()) { "Withdrawal amount must be positive" }

        val totalAfterDraw = loan.disbursedAmount + amount
        require(totalAfterDraw <= loan.approvedAmount) {
            "Withdrawal would exceed approved credit limit of ${loan.approvedAmount}"
        }

        val drawFee = feeCore.calculateDrawFee(amount, product.withdrawalConfig.drawFee)

        val updatedLoan = loan.copy(
            outstandingPrincipal = loan.outstandingPrincipal + amount,
            outstandingFees = loan.outstandingFees + drawFee,
            disbursedAmount = loan.disbursedAmount + amount,
            numberOfWithdrawals = loan.numberOfWithdrawals + 1,
        )

        val runningBalance = updatedLoan.outstandingPrincipal + updatedLoan.outstandingFees

        val effects = mutableListOf<LoanEffect>(
            LoanEffect.PersistLoan(updatedLoan),
            LoanEffect.PersistLedgerEntry(
                LedgerEntry(
                    loanId = loan.id,
                    entryType = LedgerEntryType.DISBURSEMENT,
                    amount = amount,
                    runningBalance = runningBalance,
                    description = "Credit line withdrawal #${updatedLoan.numberOfWithdrawals}",
                    entryDate = now,
                ),
            ),
        )

        if (drawFee.isPositive()) {
            effects += LoanEffect.PersistLedgerEntry(
                LedgerEntry(
                    loanId = loan.id,
                    entryType = LedgerEntryType.FEE_DRAW,
                    amount = drawFee,
                    runningBalance = runningBalance,
                    description = "Draw fee for withdrawal #${updatedLoan.numberOfWithdrawals}",
                    entryDate = now,
                ),
            )
        }

        effects += LoanEffect.EmitEvent(
            eventType = LoanEventType.LOAN_WITHDRAWAL_MADE,
            aggregateId = loan.id,
            payload = mapOf(
                "loanId" to loan.id.toString(),
                "amount" to amount.amount.toPlainString(),
                "withdrawalNumber" to updatedLoan.numberOfWithdrawals,
                "date" to now.toString(),
            ),
        )

        return Pair(updatedLoan, effects)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // cancelLoan
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Cancels an APPROVED (not yet disbursed) loan.
     */
    fun cancelLoan(loan: Loan, now: LocalDate): Pair<Loan, List<LoanEffect>> {
        require(loan.status == LoanStatus.APPROVED) {
            "Only APPROVED loans can be cancelled, but loan ${loan.id} is ${loan.status}"
        }

        val cancelledLoan = loan.copy(status = LoanStatus.CANCELLED)

        val effects = listOf(
            LoanEffect.PersistLoan(cancelledLoan),
            LoanEffect.EmitEvent(
                eventType = LoanEventType.LOAN_CANCELLED,
                aggregateId = loan.id,
                payload = mapOf(
                    "loanId" to loan.id.toString(),
                    "borrowerId" to loan.borrowerId.value,
                    "cancelledDate" to now.toString(),
                ),
            ),
            LoanEffect.SendNotification(
                recipientId = loan.borrowerId,
                message = "Your loan has been cancelled.",
            ),
        )

        return Pair(cancelledLoan, effects)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // processPayment
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Processes a payment on an ACTIVE loan.
     * Allocates to fees → interest → principal.
     * Transitions to PAID_OFF if fully settled.
     */
    fun processPayment(
        loan: Loan,
        product: LendingProduct,
        paymentAmount: Money,
        now: LocalDate,
    ): Pair<Loan, List<LoanEffect>> {
        require(loan.status == LoanStatus.ACTIVE) {
            "Loan ${loan.id} must be ACTIVE to accept payments, but is ${loan.status}"
        }
        require(paymentAmount.isPositive()) { "Payment amount must be positive" }

        val allocationResult = paymentCore.allocatePayment(loan, paymentAmount, now, product.paymentHierarchy)
        var updatedLoan = allocationResult.updatedLoan
        val ledgerEntries = allocationResult.ledgerEntries

        val isFullyPaid = updatedLoan.outstandingPrincipal.isZero() &&
            updatedLoan.outstandingInterest.isZero() &&
            updatedLoan.outstandingFees.isZero() &&
            updatedLoan.outstandingTaxOnInterest.isZero()

        val cooldownUntil = if (isFullyPaid) {
            cooldownCore.cooldownExpiresOn(now, product.cooldownConfig.afterPayoffDays)
        } else null

        if (isFullyPaid) {
            updatedLoan = updatedLoan.copy(
                status = LoanStatus.PAID_OFF,
                payoffDate = now,
                cooldownUntil = cooldownUntil,
                nextPaymentDueDate = null,
            )
        } else {
            // Advance due date if payment was made (simple monthly advancement)
            val nextDue = loan.nextPaymentDueDate?.plusMonths(1)
            updatedLoan = updatedLoan.copy(nextPaymentDueDate = nextDue)
        }

        val effects = mutableListOf<LoanEffect>()
        effects += LoanEffect.PersistLoan(updatedLoan)
        ledgerEntries.forEach { effects += LoanEffect.PersistLedgerEntry(it) }

        if (isFullyPaid) {
            effects += LoanEffect.EmitEvent(
                eventType = LoanEventType.LOAN_PAID_OFF,
                aggregateId = loan.id,
                payload = mapOf(
                    "loanId" to loan.id.toString(),
                    "borrowerId" to loan.borrowerId.value,
                    "payoffDate" to now.toString(),
                    "cooldownUntil" to (cooldownUntil?.toString() ?: "none"),
                ),
            )
            effects += LoanEffect.SendNotification(
                recipientId = loan.borrowerId,
                message = "Congratulations! Your loan has been fully paid off.",
            )
        } else {
            effects += LoanEffect.EmitEvent(
                eventType = LoanEventType.LOAN_PAYMENT_RECEIVED,
                aggregateId = loan.id,
                payload = mapOf(
                    "loanId" to loan.id.toString(),
                    "amount" to paymentAmount.amount.toPlainString(),
                    "date" to now.toString(),
                    "remainingBalance" to (updatedLoan.outstandingPrincipal + updatedLoan.outstandingInterest + updatedLoan.outstandingFees).amount.toPlainString(),
                ),
            )
        }

        return Pair(updatedLoan, effects)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // accrueInterest
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Accrues one day of interest on an ACTIVE loan.
     */
    fun accrueInterest(
        loan: Loan,
        product: LendingProduct,
        asOf: LocalDate,
    ): Pair<Loan, List<LoanEffect>> {
        require(loan.status == LoanStatus.ACTIVE) {
            "Cannot accrue interest on loan ${loan.id} with status ${loan.status}"
        }

        val dailyInterest = interestCore.calculateDailyAccrual(
            loan.outstandingPrincipal,
            product.interestConfig.annualRate,
        )

        if (dailyInterest.isZero()) return Pair(loan, emptyList())

        val updatedLoan = loan.copy(
            outstandingInterest = loan.outstandingInterest + dailyInterest,
        )

        val runningBalance = updatedLoan.outstandingPrincipal +
            updatedLoan.outstandingInterest + updatedLoan.outstandingFees

        val entry = LedgerEntry(
            loanId = loan.id,
            entryType = LedgerEntryType.INTEREST_ACCRUAL,
            amount = dailyInterest,
            runningBalance = runningBalance,
            description = "Daily interest accrual",
            entryDate = asOf,
        )

        val effects = listOf(
            LoanEffect.PersistLoan(updatedLoan),
            LoanEffect.PersistLedgerEntry(entry),
        )

        return Pair(updatedLoan, effects)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // accrueLateFee
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Applies a late fee if the grace period has elapsed and no prior late fee
     * for the current cycle has been assessed.
     */
    fun accrueLateFee(
        loan: Loan,
        product: LendingProduct,
        asOf: LocalDate,
    ): Pair<Loan, List<LoanEffect>> {
        if (loan.status != LoanStatus.ACTIVE) return Pair(loan, emptyList())

        val lateFee = feeCore.calculateLateFee(
            loan.nextPaymentDueDate,
            product.interestConfig.gracePeriodDays,
            product.feeConfig.lateFee,
            asOf,
        )

        if (lateFee.isZero()) return Pair(loan, emptyList())

        val updatedLoan = loan.copy(outstandingFees = loan.outstandingFees + lateFee)

        val runningBalance = updatedLoan.outstandingPrincipal +
            updatedLoan.outstandingInterest + updatedLoan.outstandingFees

        val entry = LedgerEntry(
            loanId = loan.id,
            entryType = LedgerEntryType.FEE_LATE,
            amount = lateFee,
            runningBalance = runningBalance,
            description = "Late fee assessed",
            entryDate = asOf,
        )

        val effects = listOf(
            LoanEffect.PersistLoan(updatedLoan),
            LoanEffect.PersistLedgerEntry(entry),
            LoanEffect.SendNotification(
                recipientId = loan.borrowerId,
                message = "A late fee of ${lateFee} has been applied to your loan.",
            ),
        )

        return Pair(updatedLoan, effects)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun calculateNextDueDate(disbursementDate: LocalDate, product: LendingProduct): LocalDate =
        when (product.paymentConfig.cycle) {
            com.lending.poc.domain.model.PaymentCycle.MONTHLY -> disbursementDate.plusMonths(1)
            com.lending.poc.domain.model.PaymentCycle.WEEKLY -> disbursementDate.plusWeeks(1)
            com.lending.poc.domain.model.PaymentCycle.BIWEEKLY -> disbursementDate.plusWeeks(2)
            com.lending.poc.domain.model.PaymentCycle.SINGLE -> disbursementDate.plusMonths(
                product.paymentConfig.numberOfCycles?.toLong() ?: 12L,
            )
        }
}
