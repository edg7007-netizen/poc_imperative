package com.lending.poc.web.dto

import com.lending.poc.domain.model.LedgerEntry
import com.lending.poc.domain.model.Loan
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class LoanResponse(
    val id: UUID,
    val borrowerId: String,
    val productId: String,
    val status: String,
    val approvedAmount: BigDecimal,
    val currency: String,
    val outstandingPrincipal: BigDecimal,
    val outstandingInterest: BigDecimal,
    val outstandingFees: BigDecimal,
    val disbursedAmount: BigDecimal,
    val numberOfWithdrawals: Int,
    val originationDate: LocalDate?,
    val disbursementDate: LocalDate?,
    val nextPaymentDueDate: LocalDate?,
    val payoffDate: LocalDate?,
    val cooldownUntil: LocalDate?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(loan: Loan) = LoanResponse(
            id = loan.id,
            borrowerId = loan.borrowerId,
            productId = loan.productId,
            status = loan.status.name,
            approvedAmount = loan.approvedAmount.amount,
            currency = loan.approvedAmount.currency,
            outstandingPrincipal = loan.outstandingPrincipal.amount,
            outstandingInterest = loan.outstandingInterest.amount,
            outstandingFees = loan.outstandingFees.amount,
            disbursedAmount = loan.disbursedAmount.amount,
            numberOfWithdrawals = loan.numberOfWithdrawals,
            originationDate = loan.originationDate,
            disbursementDate = loan.disbursementDate,
            nextPaymentDueDate = loan.nextPaymentDueDate,
            payoffDate = loan.payoffDate,
            cooldownUntil = loan.cooldownUntil,
            createdAt = loan.createdAt,
            updatedAt = loan.updatedAt,
        )
    }
}

data class LedgerEntryResponse(
    val id: UUID,
    val loanId: UUID,
    val entryType: String,
    val amount: BigDecimal,
    val runningBalance: BigDecimal,
    val currency: String,
    val description: String,
    val entryDate: LocalDate,
) {
    companion object {
        fun from(entry: LedgerEntry) = LedgerEntryResponse(
            id = entry.id,
            loanId = entry.loanId,
            entryType = entry.entryType.name,
            amount = entry.amount.amount,
            runningBalance = entry.runningBalance.amount,
            currency = entry.amount.currency,
            description = entry.description,
            entryDate = entry.entryDate,
        )
    }
}
