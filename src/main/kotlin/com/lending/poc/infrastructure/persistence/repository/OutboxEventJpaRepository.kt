package com.lending.poc.infrastructure.persistence.repository

import com.lending.poc.infrastructure.persistence.entity.OutboxEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, UUID> {
    fun findByProcessedFalse(): List<OutboxEventEntity>
}
