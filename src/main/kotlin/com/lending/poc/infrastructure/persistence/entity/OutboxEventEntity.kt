package com.lending.poc.infrastructure.persistence.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(

    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "event_type", nullable = false)
    val eventType: String = "",

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: UUID = UUID.randomUUID(),

    @Column(name = "aggregate_type", nullable = false)
    val aggregateType: String = "Loan",

    @Column(columnDefinition = "TEXT", nullable = false)
    val payload: String = "",

    @Column(nullable = false)
    var processed: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,
)
