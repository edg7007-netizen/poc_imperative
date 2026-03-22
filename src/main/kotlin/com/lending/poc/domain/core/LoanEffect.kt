package com.lending.poc.domain.core

import com.lending.poc.domain.model.*
import java.time.LocalDate
import java.util.UUID



/**
 * Sealed hierarchy of every possible side-effect the functional core can request.
 * The core NEVER executes these — it only returns them as data.
 * The imperative shell is responsible for execution.
 */
sealed class LoanEffect {

    // ── Persistence effects ──────────────────────────────────────────────────
    data class PersistLoan(val loan: Loan) : LoanEffect()
    data class PersistLedgerEntry(val entry: LedgerEntry) : LoanEffect()

    // ── Domain event effects (written to outbox in the same transaction) ──────
    data class EmitEvent(
        val eventType: LoanEventType,
        val aggregateId: UUID,
        val payload: Map<String, Any>,
    ) : LoanEffect()

    // ── Notification effects ─────────────────────────────────────────────────
    data class SendNotification(val recipientId: BorrowerId, val message: String) : LoanEffect()

    // ── Scheduling effects ───────────────────────────────────────────────────
    data class ScheduleAccrual(val loanId: UUID, val nextAccrualDate: LocalDate) : LoanEffect()
}
