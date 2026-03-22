package com.lending.poc.infrastructure.persistence.mapper

import com.lending.poc.domain.model.*
import com.lending.poc.infrastructure.persistence.entity.LedgerEntryEntity
import com.lending.poc.infrastructure.persistence.entity.LoanEntity
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
object LoanMapper {

    fun toDomain(entity: LoanEntity): Loan = Loan(
        id = entity.id,
        borrowerId = entity.borrowerId,
        productId = entity.productId,
        status = LoanStatus.valueOf(entity.status),
        approvedAmount = Money(entity.approvedAmount, entity.currency),
        outstandingPrincipal = Money(entity.outstandingPrincipal, entity.currency),
        outstandingInterest = Money(entity.outstandingInterest, entity.currency),
        outstandingFees = Money(entity.outstandingFees, entity.currency),
        disbursedAmount = Money(entity.disbursedAmount, entity.currency),
        numberOfWithdrawals = entity.numberOfWithdrawals,
        originationDate = entity.originationDate,
        disbursementDate = entity.disbursementDate,
        nextPaymentDueDate = entity.nextPaymentDueDate,
        payoffDate = entity.payoffDate,
        cooldownUntil = entity.cooldownUntil,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    fun toEntity(loan: Loan): LoanEntity = LoanEntity(
        id = loan.id,
        borrowerId = loan.borrowerId,
        productId = loan.productId,
        status = loan.status.name,
        approvedAmount = loan.approvedAmount.amount,
        outstandingPrincipal = loan.outstandingPrincipal.amount,
        outstandingInterest = loan.outstandingInterest.amount,
        outstandingFees = loan.outstandingFees.amount,
        disbursedAmount = loan.disbursedAmount.amount,
        numberOfWithdrawals = loan.numberOfWithdrawals,
        currency = loan.approvedAmount.currency,
        originationDate = loan.originationDate,
        disbursementDate = loan.disbursementDate,
        nextPaymentDueDate = loan.nextPaymentDueDate,
        payoffDate = loan.payoffDate,
        cooldownUntil = loan.cooldownUntil,
        createdAt = loan.createdAt,
        updatedAt = loan.updatedAt,
    )

    fun ledgerEntryToDomain(entity: LedgerEntryEntity): LedgerEntry = LedgerEntry(
        id = entity.id,
        loanId = entity.loanId,
        entryType = LedgerEntryType.valueOf(entity.entryType),
        amount = Money(entity.amount, entity.currency),
        runningBalance = Money(entity.runningBalance, entity.currency),
        description = entity.description,
        entryDate = entity.entryDate,
    )

    fun ledgerEntryToEntity(entry: LedgerEntry): LedgerEntryEntity = LedgerEntryEntity(
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
