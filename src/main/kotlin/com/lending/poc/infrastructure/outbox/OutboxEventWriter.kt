package com.lending.poc.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.lending.poc.infrastructure.persistence.entity.OutboxEventEntity
import com.lending.poc.infrastructure.persistence.repository.OutboxEventJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Writes domain events to the outbox table BEFORE the transaction commits.
 * Because @TransactionalEventListener with BEFORE_COMMIT participates in the outer transaction,
 * the outbox record and the state change are always written atomically.
 */
@Component
class OutboxEventWriter(
    private val outboxRepo: OutboxEventJpaRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(OutboxEventWriter::class.java)

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onLoanDomainEvent(event: LoanDomainEvent) {
        val effect = event.effect
        log.debug("OutboxEventWriter: writing outbox record for ${effect.eventType} aggregateId=${effect.aggregateId}")
        val payloadJson = objectMapper.writeValueAsString(effect.payload)
        val outboxEvent = OutboxEventEntity(
            eventType = effect.eventType.name,
            aggregateId = effect.aggregateId,
            aggregateType = "Loan",
            payload = payloadJson,
        )
        outboxRepo.save(outboxEvent)
    }
}
