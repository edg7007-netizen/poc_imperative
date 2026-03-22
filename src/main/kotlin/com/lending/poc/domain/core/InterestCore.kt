package com.lending.poc.domain.core

import com.lending.poc.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Pure interest calculation functions — no I/O, no state, fully deterministic.
 */
@org.springframework.stereotype.Component
class InterestCore {

    private val DAYS_IN_YEAR = BigDecimal.valueOf(365)

    /**
     * Calculates one day of accrued interest on [outstandingPrincipal].
     *
     * dailyRate = annualRate / 365
     * dailyInterest = outstandingPrincipal * dailyRate
     */
    fun calculateDailyAccrual(
        outstandingPrincipal: Money,
        annualRate: BigDecimal,
    ): Money {
        if (outstandingPrincipal.isZero() || outstandingPrincipal.isNegative()) return Money.ZERO
        val dailyRate = annualRate.divide(DAYS_IN_YEAR, 10, RoundingMode.HALF_UP)
        return outstandingPrincipal * dailyRate
    }

    /**
     * Calculates interest for a multi-day period (e.g., accrual catch-up).
     * Computes the period directly to avoid rounding-per-day accumulation.
     */
    fun calculateAccrualForPeriod(
        outstandingPrincipal: Money,
        annualRate: BigDecimal,
        days: Int,
    ): Money {
        if (days <= 0) return Money.ZERO
        if (outstandingPrincipal.isZero() || outstandingPrincipal.isNegative()) return Money.ZERO
        val periodRate = annualRate
            .multiply(BigDecimal.valueOf(days.toLong()))
            .divide(DAYS_IN_YEAR, 10, RoundingMode.HALF_UP)
        return outstandingPrincipal * periodRate
    }

    /**
     * Calculates fixed upfront interest (interest charged once at disbursement).
     * interest = principal * annualRate * (termMonths / 12)
     */
    fun calculateFixedUpfrontInterest(
        principal: Money,
        annualRate: BigDecimal,
        termMonths: Int,
    ): Money {
        val termFraction = BigDecimal.valueOf(termMonths.toLong())
            .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
        return principal * (annualRate * termFraction)
    }

    /**
     * Calculates the minimum monthly payment for a revolving line:
     * minimum = outstandingInterest + (outstandingPrincipal * 1%)
     */
    fun calculateRevolvingMinimumPayment(
        outstandingPrincipal: Money,
        outstandingInterest: Money,
    ): Money {
        val onePercent = BigDecimal("0.01")
        return outstandingInterest + (outstandingPrincipal * onePercent)
    }

    /**
     * Calculates the fixed monthly installment payment using standard amortization formula.
     * M = P * [r(1+r)^n] / [(1+r)^n - 1]
     * where r = monthly rate, n = number of payments
     */
    fun calculateFixedMonthlyInstallment(
        principal: Money,
        annualRate: BigDecimal,
        numberOfMonths: Int,
    ): Money {
        if (numberOfMonths <= 0) return principal
        val monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            // Zero interest: simply divide principal by number of months
            return Money(
                principal.amount.divide(BigDecimal.valueOf(numberOfMonths.toLong()), 2, RoundingMode.HALF_UP),
                principal.currency,
            )
        }
        val onePlusR = BigDecimal.ONE + monthlyRate
        val onePlusRPowN = onePlusR.pow(numberOfMonths)
        val numerator = monthlyRate * onePlusRPowN
        val denominator = onePlusRPowN - BigDecimal.ONE
        val factor = numerator.divide(denominator, 10, RoundingMode.HALF_UP)
        return principal * factor
    }

    /**
     * Generates a full amortization schedule for a fixed-installment loan.
     *
     * Each row shows the opening balance, scheduled payment, interest component,
     * principal component, and closing balance for every period.
     *
     * @param principal      Initial loan amount.
     * @param annualRate     Annual interest rate (e.g., 0.24 for 24%).
     * @param numberOfMonths Number of monthly payments.
     * @param startDate      Disbursement date; first payment is one month later.
     * @return               List of [AmortizationRow], one per period.
     */
    fun generateAmortizationSchedule(
        principal: Money,
        annualRate: BigDecimal,
        numberOfMonths: Int,
        startDate: LocalDate,
    ): List<AmortizationRow> {
        require(numberOfMonths > 0) { "numberOfMonths must be positive" }
        val monthlyPayment = calculateFixedMonthlyInstallment(principal, annualRate, numberOfMonths)
        val monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)

        val rows = mutableListOf<AmortizationRow>()
        var balance = principal

        for (period in 1..numberOfMonths) {
            val openingBalance = balance
            val interestForPeriod = balance * monthlyRate
            val principalForPeriod = if (period == numberOfMonths) {
                balance
            } else {
                (monthlyPayment - interestForPeriod).let { p ->
                    if (p > balance) balance else p
                }
            }
            val actualPayment = if (period == numberOfMonths) {
                principalForPeriod + interestForPeriod
            } else {
                monthlyPayment
            }
            balance = openingBalance - principalForPeriod

            rows += AmortizationRow(
                period = period,
                paymentDate = startDate.plusMonths(period.toLong()),
                openingBalance = openingBalance,
                scheduledPayment = actualPayment,
                principalComponent = principalForPeriod,
                interestComponent = interestForPeriod,
                closingBalance = balance,
            )
        }
        return rows
    }
}
