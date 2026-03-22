package com.lending.poc.infrastructure.outbox

import com.lending.poc.domain.core.LoanEffect
import org.springframework.context.ApplicationEvent

/**
 * Spring ApplicationEvent wrapping a [LoanEffect.EmitEvent].
 * Published by [com.lending.poc.infrastructure.effect.EffectExecutor] within the active transaction.
 * Consumed by [OutboxEventWriter] via @TransactionalEventListener(BEFORE_COMMIT) to write the
 * outbox record atomically in the same transaction — guaranteeing at-least-once delivery.
 */
class LoanDomainEvent(source: Any, val effect: LoanEffect.EmitEvent) : ApplicationEvent(source)
