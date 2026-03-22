package com.lending.poc.infrastructure.effect

import com.fasterxml.jackson.databind.ObjectMapper
import com.lending.poc.domain.core.LoanEffect
import com.lending.poc.infrastructure.persistence.entity.OutboxEventEntity
import com.lending.poc.infrastructure.persistence.mapper.LoanMapper
import com.lending.poc.infrastructure.persistence.repository.LedgerEntryJpaRepository
import com.lending.poc.infrastructure.persistence.repository.LoanJpaRepository
import com.lending.poc.infrastructure.persistence.repository.OutboxEventJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Imperative shell: executes the list of [LoanEffect] objects returned by the functional core.
 *
 * Must run inside an existing transaction (Propagation.MANDATORY) so that all persistence
 * operations and outbox writes are atomic with the outer transaction.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class EffectExecutor(
    private val loanJpaRepository: LoanJpaRepository,
    private val ledgerEntryJpaRepository: LedgerEntryJpaRepository,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(EffectExecutor::class.java)

    fun executeAll(effects: List<LoanEffect>) {
        effects.forEach { execute(it) }
    }

    private fun execute(effect: LoanEffect) = when (effect) {
        is LoanEffect.PersistLoan -> {
            log.debug("Persisting loan ${effect.loan.id} with status ${effect.loan.status}")
            loanJpaRepository.save(LoanMapper.toEntity(effect.loan))
        }

        is LoanEffect.PersistLedgerEntry -> {
            log.debug("Persisting ledger entry ${effect.entry.entryType} for loan ${effect.entry.loanId}")
            ledgerEntryJpaRepository.save(LoanMapper.ledgerEntryToEntity(effect.entry))
        }

        is LoanEffect.EmitEvent -> {
            log.debug("Writing outbox event ${effect.eventType} for aggregate ${effect.aggregateId}")
            val payloadJson = objectMapper.writeValueAsString(effect.payload)
            val outboxEvent = OutboxEventEntity(
                eventType = effect.eventType,
                aggregateId = effect.aggregateId,
                aggregateType = "Loan",
                payload = payloadJson,
            )
            outboxEventJpaRepository.save(outboxEvent)
        }

        is LoanEffect.SendNotification -> {
            // POC: log notification; in production, integrate with notification service
            log.info("NOTIFICATION → [${effect.recipientId}]: ${effect.message}")
        }

        is LoanEffect.ScheduleAccrual -> {
            // POC: the AccrualScheduler handles daily accrual via cron
            log.debug("Accrual scheduled for loan ${effect.loanId} on ${effect.nextAccrualDate}")
        }
    }
}
