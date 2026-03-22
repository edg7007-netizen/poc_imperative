package com.lending.poc.infrastructure.persistence.repository

import com.lending.poc.infrastructure.persistence.entity.LedgerEntryEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LedgerEntryJpaRepository : JpaRepository<LedgerEntryEntity, UUID> {
    fun findByLoanIdOrderByEntryDateAsc(loanId: UUID): List<LedgerEntryEntity>
}
