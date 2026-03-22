package com.lending.poc.application

import com.lending.poc.domain.core.LoanLifecycleCore
import com.lending.poc.domain.model.*
import com.lending.poc.domain.product.ProductCatalog
import com.lending.poc.infrastructure.effect.EffectExecutor
import com.lending.poc.infrastructure.persistence.mapper.LoanMapper
import com.lending.poc.infrastructure.persistence.repository.LedgerEntryJpaRepository
import com.lending.poc.infrastructure.persistence.repository.LoanJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * Imperative shell: orchestrates loan lifecycle operations.
 *
 * Each method:
 *  1. Loads current state from the database
 *  2. Calls the pure functional core → receives (newState, effects)
 *  3. Delegates effect execution to [EffectExecutor] (within the same transaction)
 */
@Service
@Transactional
class LoanApplicationService(
    private val loanJpaRepository: LoanJpaRepository,
    private val ledgerEntryJpaRepository: LedgerEntryJpaRepository,
    private val effectExecutor: EffectExecutor,
    private val loanMapper: LoanMapper,
) {

    fun applyForLoan(productId: String, borrowerId: String, requestedAmount: Money): Loan {
        val product = ProductCatalog.findById(productId)
            ?: throw IllegalArgumentException("Unknown product: $productId")

        val (loan, effects) = LoanLifecycleCore.approveLoan(product, borrowerId, requestedAmount, LocalDate.now())
        effectExecutor.executeAll(effects)
        return loan
    }

    fun disburseLoan(loanId: UUID, amount: Money): Loan {
        val loanEntity = loanJpaRepository.findById(loanId).orElseThrow {
            IllegalArgumentException("Loan not found: $loanId")
        }
        val loan = loanMapper.toDomain(loanEntity)
        val product = ProductCatalog.findById(loan.productId)
            ?: throw IllegalArgumentException("Unknown product: ${loan.productId}")

        val (updatedLoan, effects) = LoanLifecycleCore.disburseLoan(loan, product, amount, LocalDate.now())
        effectExecutor.executeAll(effects)
        return updatedLoan
    }

    fun cancelLoan(loanId: UUID): Loan {
        val loanEntity = loanJpaRepository.findById(loanId).orElseThrow {
            IllegalArgumentException("Loan not found: $loanId")
        }
        val loan = loanMapper.toDomain(loanEntity)

        val (updatedLoan, effects) = LoanLifecycleCore.cancelLoan(loan, LocalDate.now())
        effectExecutor.executeAll(effects)
        return updatedLoan
    }

    fun processPayment(loanId: UUID, amount: Money): Loan {
        val loanEntity = loanJpaRepository.findById(loanId).orElseThrow {
            IllegalArgumentException("Loan not found: $loanId")
        }
        val loan = loanMapper.toDomain(loanEntity)
        val product = ProductCatalog.findById(loan.productId)
            ?: throw IllegalArgumentException("Unknown product: ${loan.productId}")

        val (updatedLoan, effects) = LoanLifecycleCore.processPayment(loan, product, amount, LocalDate.now())
        effectExecutor.executeAll(effects)
        return updatedLoan
    }

    fun withdrawFromCreditLine(loanId: UUID, amount: Money): Loan {
        val loanEntity = loanJpaRepository.findById(loanId).orElseThrow {
            IllegalArgumentException("Loan not found: $loanId")
        }
        val loan = loanMapper.toDomain(loanEntity)
        val product = ProductCatalog.findById(loan.productId)
            ?: throw IllegalArgumentException("Unknown product: ${loan.productId}")

        val (updatedLoan, effects) = LoanLifecycleCore.withdrawFromCreditLine(loan, product, amount, LocalDate.now())
        effectExecutor.executeAll(effects)
        return updatedLoan
    }

    @Transactional(readOnly = true)
    fun getLoan(loanId: UUID): Loan {
        val loanEntity = loanJpaRepository.findById(loanId).orElseThrow {
            IllegalArgumentException("Loan not found: $loanId")
        }
        return loanMapper.toDomain(loanEntity)
    }

    @Transactional(readOnly = true)
    fun getLedger(loanId: UUID): List<LedgerEntry> {
        return ledgerEntryJpaRepository.findByLoanIdOrderByEntryDateAsc(loanId)
            .map { loanMapper.ledgerEntryToDomain(it) }
    }

    fun runDailyAccrualForActiveLoan(loanId: UUID): Loan {
        val loanEntity = loanJpaRepository.findById(loanId).orElseThrow {
            IllegalArgumentException("Loan not found: $loanId")
        }
        val loan = loanMapper.toDomain(loanEntity)
        val product = ProductCatalog.findById(loan.productId)
            ?: throw IllegalArgumentException("Unknown product: ${loan.productId}")

        val today = LocalDate.now()
        val (afterInterest, interestEffects) = LoanLifecycleCore.accrueInterest(loan, product, today)
        effectExecutor.executeAll(interestEffects)

        val (afterFees, feeEffects) = LoanLifecycleCore.accrueLateFee(afterInterest, product, today)
        effectExecutor.executeAll(feeEffects)

        return afterFees
    }

    @Transactional(readOnly = true)
    fun findAllActiveLoans(): List<Loan> =
        loanJpaRepository.findByStatus(LoanStatus.ACTIVE.name)
            .map { loanMapper.toDomain(it) }
}
