package com.lending.poc.infrastructure.persistence.repository

import com.lending.poc.infrastructure.persistence.entity.LoanEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LoanJpaRepository : JpaRepository<LoanEntity, UUID> {
    fun findByBorrowerId(borrowerId: String): List<LoanEntity>
    fun findByStatus(status: String): List<LoanEntity>
}
