package com.lending.poc.domain.core

import com.lending.poc.domain.model.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

class PaymentCoreTest {

    private val today = LocalDate.of(2024, 6, 15)
    private val loanId = UUID.randomUUID()

    private fun makeLoan(
        principal: Money = Money.of(10_000L),
        interest: Money = Money.of(100L),
        fees: Money = Money.of(50L),
    ) = Loan(
        id = loanId,
        borrowerId = "borrower-1",
        productId = "PERSONAL_INSTALLMENT_LOAN",
        status = LoanStatus.ACTIVE,
        approvedAmount = principal,
        outstandingPrincipal = principal,
        outstandingInterest = interest,
        outstandingFees = fees,
        disbursedAmount = principal,
        numberOfWithdrawals = 1,
        originationDate = today.minusMonths(1),
        disbursementDate = today.minusMonths(1),
        nextPaymentDueDate = today.plusDays(15),
        payoffDate = null,
        cooldownUntil = null,
    )

    @Test
    fun `payment allocates to fees first`() {
        val loan = makeLoan(fees = Money.of(50L), interest = Money.of(100L), principal = Money.of(1_000L))
        val result = PaymentCore.allocatePayment(loan, Money.of(30L), today)

        assertThat(result.updatedLoan.outstandingFees.amount).isEqualByComparingTo("20.00")
        assertThat(result.updatedLoan.outstandingInterest).isEqualTo(Money.of(100L))
        assertThat(result.updatedLoan.outstandingPrincipal).isEqualTo(Money.of(1_000L))
        assertThat(result.remainingAmount.isZero()).isTrue()
    }

    @Test
    fun `payment allocates to interest after clearing fees`() {
        val loan = makeLoan(fees = Money.of(50L), interest = Money.of(100L), principal = Money.of(1_000L))
        val result = PaymentCore.allocatePayment(loan, Money.of(80L), today)

        assertThat(result.updatedLoan.outstandingFees.isZero()).isTrue()
        assertThat(result.updatedLoan.outstandingInterest.amount).isEqualByComparingTo("70.00")
        assertThat(result.updatedLoan.outstandingPrincipal).isEqualTo(Money.of(1_000L))
    }

    @Test
    fun `payment allocates to principal after clearing fees and interest`() {
        val loan = makeLoan(fees = Money.of(50L), interest = Money.of(100L), principal = Money.of(1_000L))
        val result = PaymentCore.allocatePayment(loan, Money.of(200L), today)

        assertThat(result.updatedLoan.outstandingFees.isZero()).isTrue()
        assertThat(result.updatedLoan.outstandingInterest.isZero()).isTrue()
        assertThat(result.updatedLoan.outstandingPrincipal.amount).isEqualByComparingTo("950.00")
    }

    @Test
    fun `overpayment returns remaining amount`() {
        val loan = makeLoan(fees = Money.of(0L), interest = Money.of(0L), principal = Money.of(100L))
        val result = PaymentCore.allocatePayment(loan, Money.of(150L), today)

        assertThat(result.updatedLoan.outstandingPrincipal.isZero()).isTrue()
        assertThat(result.remainingAmount.amount).isEqualByComparingTo("50.00")
    }

    @Test
    fun `payment produces ledger entries for each allocated bucket`() {
        val loan = makeLoan(fees = Money.of(50L), interest = Money.of(100L), principal = Money.of(1_000L))
        val result = PaymentCore.allocatePayment(loan, Money.of(300L), today)

        // Should have 3 entries: fees, interest, principal
        assertThat(result.ledgerEntries).hasSize(3)
        assertThat(result.ledgerEntries.all { it.entryType == LedgerEntryType.PAYMENT }).isTrue()
    }

    @Test
    fun `exact payoff amount results in zero outstanding balances`() {
        val loan = makeLoan(fees = Money.of(50L), interest = Money.of(100L), principal = Money.of(1_000L))
        val totalOwed = loan.outstandingFees + loan.outstandingInterest + loan.outstandingPrincipal
        val result = PaymentCore.allocatePayment(loan, totalOwed, today)

        assertThat(result.updatedLoan.outstandingFees.isZero()).isTrue()
        assertThat(result.updatedLoan.outstandingInterest.isZero()).isTrue()
        assertThat(result.updatedLoan.outstandingPrincipal.isZero()).isTrue()
        assertThat(result.remainingAmount.isZero()).isTrue()
    }

    @Test
    fun `payment fails for zero amount`() {
        val loan = makeLoan()
        assertThrows<IllegalArgumentException> {
            PaymentCore.allocatePayment(loan, Money.ZERO, today)
        }
    }

    @Test
    fun `running balance decreases with each allocation step`() {
        val loan = makeLoan(fees = Money.of(50L), interest = Money.of(100L), principal = Money.of(1_000L))
        val result = PaymentCore.allocatePayment(loan, Money.of(1_150L), today)

        val balances = result.ledgerEntries.map { it.runningBalance.amount }
        // Each running balance should be <= the previous one
        for (i in 1 until balances.size) {
            assertThat(balances[i]).isLessThanOrEqualTo(balances[i - 1])
        }
    }
}
