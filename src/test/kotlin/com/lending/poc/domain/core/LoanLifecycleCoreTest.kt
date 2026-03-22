package com.lending.poc.domain.core

import com.lending.poc.domain.model.*
import com.lending.poc.domain.product.ProductCatalog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class LoanLifecycleCoreTest {

    private val interestCore = InterestCore()
    private val feeCore = FeeCore()
    private val paymentCore = PaymentCore()
    private val cooldownCore = CooldownCore()
    private val loanLifecycleCore = LoanLifecycleCore(interestCore, feeCore, paymentCore, cooldownCore)

    private val product = ProductCatalog.PERSONAL_INSTALLMENT_LOAN
    private val revolvingProduct = ProductCatalog.REVOLVING_LINE_OF_CREDIT
    private val today = LocalDate.of(2024, 1, 15)
    private val borrowerId = BorrowerId("borrower-1")

    // ── approveLoan ──────────────────────────────────────────────────────────

    @Test
    fun `approving a loan creates it in APPROVED status with correct amounts`() {
        val amount = Money.of(10_000L)
        val (loan, effects) = loanLifecycleCore.approveLoan(product, borrowerId, amount, today)

        assertThat(loan.status).isEqualTo(LoanStatus.APPROVED)
        assertThat(loan.borrowerId).isEqualTo(BorrowerId("borrower-1"))
        assertThat(loan.productId).isEqualTo(product.id)
        assertThat(loan.approvedAmount).isEqualTo(amount)
        assertThat(loan.outstandingPrincipal).isEqualTo(Money.ZERO)
        assertThat(loan.outstandingInterest).isEqualTo(Money.ZERO)
        assertThat(loan.disbursedAmount).isEqualTo(Money.ZERO)
        assertThat(loan.originationDate).isEqualTo(today)
    }

    @Test
    fun `approving a loan emits LOAN_APPROVED event and persists the loan`() {
        val (_, effects) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(5_000L), today)

        val persistEffect = effects.filterIsInstance<LoanEffect.PersistLoan>()
        val eventEffect = effects.filterIsInstance<LoanEffect.EmitEvent>()

        assertThat(persistEffect).hasSize(1)
        assertThat(eventEffect).hasSize(1)
        assertThat(eventEffect[0].eventType).isEqualTo(LoanEventType.LOAN_APPROVED)
    }

    @Test
    fun `approval fails when amount is below product minimum`() {
        assertThrows<IllegalArgumentException> {
            loanLifecycleCore.approveLoan(product, borrowerId, Money.of(500L), today)
        }
    }

    @Test
    fun `approval fails when amount exceeds product maximum`() {
        assertThrows<IllegalArgumentException> {
            loanLifecycleCore.approveLoan(product, borrowerId, Money.of(100_000L), today)
        }
    }

    // ── disburseLoan ─────────────────────────────────────────────────────────

    @Test
    fun `disbursing an approved loan transitions it to ACTIVE`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(10_000L), today)
        val (activeLoan, effects) = loanLifecycleCore.disburseLoan(approvedLoan, product, Money.of(10_000L), today)

        assertThat(activeLoan.status).isEqualTo(LoanStatus.ACTIVE)
        assertThat(activeLoan.outstandingPrincipal).isEqualTo(Money.of(10_000L))
        assertThat(activeLoan.disbursedAmount).isEqualTo(Money.of(10_000L))
        assertThat(activeLoan.disbursementDate).isEqualTo(today)
        assertThat(activeLoan.nextPaymentDueDate).isEqualTo(today.plusMonths(1))
    }

    @Test
    fun `disbursement calculates origination fee correctly`() {
        val principal = Money.of(10_000L)
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, principal, today)
        val (activeLoan, effects) = loanLifecycleCore.disburseLoan(approvedLoan, product, principal, today)

        // 2% of 10,000 = 200
        assertThat(activeLoan.outstandingFees).isEqualTo(Money.of(200L))

        val feeEntries = effects.filterIsInstance<LoanEffect.PersistLedgerEntry>()
            .filter { it.entry.entryType == LedgerEntryType.FEE_ORIGINATION }
        assertThat(feeEntries).hasSize(1)
        assertThat(feeEntries[0].entry.amount).isEqualTo(Money.of(200L))
    }

    @Test
    fun `cannot disburse a loan that is not APPROVED`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(10_000L), today)
        val (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, product, Money.of(10_000L), today)

        assertThrows<IllegalArgumentException> {
            loanLifecycleCore.disburseLoan(activeLoan, product, Money.of(10_000L), today)
        }
    }

    // ── cancelLoan ───────────────────────────────────────────────────────────

    @Test
    fun `cancelling an approved undisbursed loan transitions to CANCELLED`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(10_000L), today)
        val (cancelledLoan, effects) = loanLifecycleCore.cancelLoan(approvedLoan, today)

        assertThat(cancelledLoan.status).isEqualTo(LoanStatus.CANCELLED)
        val eventEffects = effects.filterIsInstance<LoanEffect.EmitEvent>()
        assertThat(eventEffects[0].eventType).isEqualTo(LoanEventType.LOAN_CANCELLED)
    }

    @Test
    fun `cannot cancel an already active loan`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(10_000L), today)
        val (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, product, Money.of(10_000L), today)

        assertThrows<IllegalArgumentException> {
            loanLifecycleCore.cancelLoan(activeLoan, today)
        }
    }

    // ── processPayment ───────────────────────────────────────────────────────

    @Test
    fun `payment allocation applies fees first then interest then principal`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(10_000L), today)
        var (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, product, Money.of(10_000L), today)

        // Manually add some interest for testing
        activeLoan = activeLoan.copy(outstandingInterest = Money.of(50L))

        // Fees = 200, Interest = 50, Principal = 10,000
        // Pay 300 → should clear all fees (200) + all interest (50) + 50 toward principal
        val paymentAmount = Money.of(300L)
        val (updatedLoan, effects) = loanLifecycleCore.processPayment(activeLoan, product, paymentAmount, today)

        assertThat(updatedLoan.outstandingFees).isEqualTo(Money.ZERO)
        assertThat(updatedLoan.outstandingInterest).isEqualTo(Money.ZERO)
        assertThat(updatedLoan.outstandingPrincipal).isEqualTo(Money.of(9_950L))
        assertThat(updatedLoan.status).isEqualTo(LoanStatus.ACTIVE)
    }

    @Test
    fun `full payment pays off the loan and sets PAID_OFF status`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(10_000L), today)
        val (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, product, Money.of(10_000L), today)

        // Pay off everything: principal (10000) + origination fee (200)
        val totalOwed = activeLoan.outstandingPrincipal + activeLoan.outstandingFees
        val (paidOffLoan, effects) = loanLifecycleCore.processPayment(activeLoan, product, totalOwed, today)

        assertThat(paidOffLoan.status).isEqualTo(LoanStatus.PAID_OFF)
        assertThat(paidOffLoan.payoffDate).isEqualTo(today)
        assertThat(paidOffLoan.outstandingPrincipal).isEqualTo(Money.ZERO)
        assertThat(paidOffLoan.outstandingFees).isEqualTo(Money.ZERO)
    }

    @Test
    fun `full payment sets cooldown until date for installment product`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(10_000L), today)
        val (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, product, Money.of(10_000L), today)
        val totalOwed = activeLoan.outstandingPrincipal + activeLoan.outstandingFees

        val (paidOffLoan, _) = loanLifecycleCore.processPayment(activeLoan, product, totalOwed, today)

        // 30-day cooldown
        assertThat(paidOffLoan.cooldownUntil).isEqualTo(today.plusDays(30))
    }

    @Test
    fun `revolving line payoff has zero cooldown`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(revolvingProduct, borrowerId, Money.of(5_000L), today)
        val (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, revolvingProduct, Money.of(5_000L), today)
        val totalOwed = activeLoan.outstandingPrincipal + activeLoan.outstandingFees

        val (paidOffLoan, _) = loanLifecycleCore.processPayment(activeLoan, revolvingProduct, totalOwed, today)

        assertThat(paidOffLoan.status).isEqualTo(LoanStatus.PAID_OFF)
        assertThat(paidOffLoan.cooldownUntil).isNull()
    }

    // ── accrueInterest ───────────────────────────────────────────────────────

    @Test
    fun `daily interest accrual increases outstanding interest`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(10_000L), today)
        val (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, product, Money.of(10_000L), today)

        val (accruedLoan, effects) = loanLifecycleCore.accrueInterest(activeLoan, product, today.plusDays(1))

        assertThat(accruedLoan.outstandingInterest.amount).isGreaterThan(java.math.BigDecimal.ZERO)

        val ledgerEntries = effects.filterIsInstance<LoanEffect.PersistLedgerEntry>()
        assertThat(ledgerEntries).hasSize(1)
        assertThat(ledgerEntries[0].entry.entryType).isEqualTo(LedgerEntryType.INTEREST_ACCRUAL)
    }

    // ── accrueLateFee ────────────────────────────────────────────────────────

    @Test
    fun `late fee is not applied before grace period expires`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(10_000L), today)
        val (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, product, Money.of(10_000L), today)

        // Due date = today + 1 month, grace = 3 days
        val beforeGracePeriod = activeLoan.nextPaymentDueDate!!.plusDays(2)
        val (updatedLoan, effects) = loanLifecycleCore.accrueLateFee(activeLoan, product, beforeGracePeriod)

        assertThat(updatedLoan.outstandingFees).isEqualTo(activeLoan.outstandingFees)
        assertThat(effects).isEmpty()
    }

    @Test
    fun `late fee is applied after grace period expires`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(product, borrowerId, Money.of(10_000L), today)
        val (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, product, Money.of(10_000L), today)

        // Due date = today + 1 month, grace = 3 days, so late fee applies after due + 3 days
        val afterGracePeriod = activeLoan.nextPaymentDueDate!!.plusDays(4)
        val (updatedLoan, _) = loanLifecycleCore.accrueLateFee(activeLoan, product, afterGracePeriod)

        // $25 late fee added to existing origination fee
        assertThat(updatedLoan.outstandingFees).isEqualTo(activeLoan.outstandingFees + Money.of(25L))
    }

    // ── revolving credit line withdrawals ────────────────────────────────────

    @Test
    fun `revolving line allows multiple withdrawals up to credit limit`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(revolvingProduct, borrowerId, Money.of(10_000L), today)
        val (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, revolvingProduct, Money.of(3_000L), today)

        val (afterSecondDraw, effects) = loanLifecycleCore.withdrawFromCreditLine(
            activeLoan, revolvingProduct, Money.of(2_000L), today,
        )

        assertThat(afterSecondDraw.outstandingPrincipal).isEqualTo(Money.of(5_000L))
        assertThat(afterSecondDraw.disbursedAmount).isEqualTo(Money.of(5_000L))
        assertThat(afterSecondDraw.numberOfWithdrawals).isEqualTo(2)
    }

    @Test
    fun `revolving line charges draw fee on each withdrawal`() {
        val (approvedLoan, _) = loanLifecycleCore.approveLoan(revolvingProduct, borrowerId, Money.of(10_000L), today)
        val (activeLoan, _) = loanLifecycleCore.disburseLoan(approvedLoan, revolvingProduct, Money.of(2_000L), today)

        // 3% draw fee on 2,000 first draw = 60
        // After second draw of 1,000: 3% of 1,000 = 30
        val (afterDraw, _) = loanLifecycleCore.withdrawFromCreditLine(
            activeLoan, revolvingProduct, Money.of(1_000L), today,
        )

        val expectedDrawFee = Money.of(30L) // 3% of 1000
        assertThat(afterDraw.outstandingFees).isEqualTo(activeLoan.outstandingFees + expectedDrawFee)
    }
}

