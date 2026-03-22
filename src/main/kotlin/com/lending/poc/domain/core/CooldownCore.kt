package com.lending.poc.domain.core

import java.time.LocalDate

/**
 * Pure cooldown eligibility check — no I/O, fully deterministic.
 */
@org.springframework.stereotype.Component
class CooldownCore {

    /**
     * Determines whether a borrower is eligible for a new loan based on cooldown rules.
     *
     * @param lastPayoffDate the date the borrower's last loan was paid off (null if no prior loan)
     * @param cooldownDays the required waiting period after payoff
     * @param today the current date to evaluate eligibility against
     * @return true if the borrower is eligible (no cooldown or cooldown has elapsed)
     */
    fun isEligibleForNewLoan(
        lastPayoffDate: LocalDate?,
        cooldownDays: Int,
        today: LocalDate,
    ): Boolean {
        if (lastPayoffDate == null) return true
        if (cooldownDays <= 0) return true
        val eligibleFrom = lastPayoffDate.plusDays(cooldownDays.toLong())
        return !today.isBefore(eligibleFrom)
    }

    /**
     * Calculates the date when the cooldown expires.
     *
     * @param payoffDate the date the loan was paid off
     * @param cooldownDays the required waiting period
     * @return the date the borrower becomes eligible again, or null if no cooldown applies
     */
    fun cooldownExpiresOn(payoffDate: LocalDate, cooldownDays: Int): LocalDate? {
        if (cooldownDays <= 0) return null
        return payoffDate.plusDays(cooldownDays.toLong())
    }
}
