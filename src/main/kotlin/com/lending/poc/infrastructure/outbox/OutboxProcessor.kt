package com.lending.poc.infrastructure.outbox

import com.lending.poc.infrastructure.persistence.repository.OutboxEventJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Scheduled outbox relay processor.
 *
 * Reads unprocessed events from the outbox_events table and "publishes" them.
 * In a real system these would be sent to Kafka / RabbitMQ / SNS.
 * For this POC they are logged and marked as processed.
 *
 * Runs every 5 seconds to demonstrate at-least-once delivery semantics.
 */
@Component
class OutboxProcessor(private val outboxRepo: OutboxEventJpaRepository) {

    private val log = LoggerFactory.getLogger(OutboxProcessor::class.java)

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun processOutbox() {
        val pending = outboxRepo.findByProcessedFalse()
        if (pending.isEmpty()) return

        log.debug("OutboxProcessor: found ${pending.size} pending event(s)")
        pending.forEach { event ->
            // In a real system: publish to Kafka/RabbitMQ/SNS/EventBridge
            log.info("Publishing event: [${event.eventType}] aggregateId=${event.aggregateId} payload=${event.payload}")
            event.processed = true
            event.processedAt = LocalDateTime.now()
            outboxRepo.save(event)
        }
    }
}
