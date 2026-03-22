package com.lending.poc.domain.core

import com.lending.poc.domain.model.*
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pure fee calculation functions — no I/O, no state, fully deterministic.
 */
object FeeCore {

    /**
     * Calculates the origination fee charged at loan disbursement.
     * originationFee = approvedAmount * originationFeeRate
     */
    fun calculateOriginationFee(approvedAmount: Money, originationFeeRate: BigDecimal): Money {
        if (originationFeeRate.compareTo(BigDecimal.ZERO) == 0) return Money.ZERO
        return approvedAmount * originationFeeRate
    }

    /**
     * Calculates the draw fee charged per withdrawal for revolving lines.
     * drawFee = drawnAmount * drawFeeRate
     */
    fun calculateDrawFee(drawnAmount: Money, drawFeeRate: BigDecimal): Money {
        if (drawFeeRate.compareTo(BigDecimal.ZERO) == 0) return Money.ZERO
        return drawnAmount * drawFeeRate
    }

    /**
     * Determines whether a late fee should be applied.
     *
     * @param nextPaymentDueDate the due date for the payment
     * @param gracePeriodDays grace period after due date before late fee applies
     * @param asOf the date to evaluate against
     * @return true if the grace period has elapsed and a late fee should be charged
     */
    fun isLateFeeApplicable(
        nextPaymentDueDate: LocalDate,
        gracePeriodDays: Int,
        asOf: LocalDate,
    ): Boolean = asOf.isAfter(nextPaymentDueDate.plusDays(gracePeriodDays.toLong()))

    /**
     * Returns the flat late fee amount from config if applicable, otherwise Money.ZERO.
     */
    fun calculateLateFee(
        nextPaymentDueDate: LocalDate?,
        gracePeriodDays: Int,
        lateFeeAmount: Money,
        asOf: LocalDate,
    ): Money {
        if (nextPaymentDueDate == null) return Money.ZERO
        return if (isLateFeeApplicable(nextPaymentDueDate, gracePeriodDays, asOf)) lateFeeAmount
        else Money.ZERO
    }
}
