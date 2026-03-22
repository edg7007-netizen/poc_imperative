package com.lending.poc.domain.core

import com.lending.poc.domain.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode

class InterestCoreTest {

    private val annualRate24pct = BigDecimal("0.24")
    private val annualRate18pct = BigDecimal("0.18")

    @Test
    fun `daily accrual computes correct amount for 24pct APR`() {
        val principal = Money.of(10_000L)
        // Expected: 10,000 * 0.24 / 365 ≈ 6.5753...  → rounded to $6.58
        val daily = InterestCore.calculateDailyAccrual(principal, annualRate24pct)
        assertThat(daily.amount).isEqualByComparingTo("6.58")
    }

    @Test
    fun `daily accrual computes correct amount for 18pct APR`() {
        val principal = Money.of(5_000L)
        // Expected: 5,000 * 0.18 / 365 ≈ 2.4657... → $2.47
        val daily = InterestCore.calculateDailyAccrual(principal, annualRate18pct)
        assertThat(daily.amount).isEqualByComparingTo("2.47")
    }

    @Test
    fun `daily accrual for 30-day period`() {
        val principal = Money.of(10_000L)
        val thirtyDayInterest = InterestCore.calculateAccrualForPeriod(principal, annualRate24pct, 30)
        // ~10,000 * 0.24 / 365 * 30 ≈ 197.26
        assertThat(thirtyDayInterest.amount).isEqualByComparingTo("197.26")
    }

    @Test
    fun `daily accrual returns ZERO for zero principal`() {
        val daily = InterestCore.calculateDailyAccrual(Money.ZERO, annualRate24pct)
        assertThat(daily.isZero()).isTrue()
    }

    @Test
    fun `daily accrual returns ZERO for negative principal`() {
        val negativePrincipal = Money(BigDecimal("-100.00"))
        val daily = InterestCore.calculateDailyAccrual(negativePrincipal, annualRate24pct)
        assertThat(daily.isZero()).isTrue()
    }

    @Test
    fun `accrual for zero days returns ZERO`() {
        val result = InterestCore.calculateAccrualForPeriod(Money.of(10_000L), annualRate24pct, 0)
        assertThat(result.isZero()).isTrue()
    }

    @Test
    fun `fixed upfront interest calculation`() {
        val principal = Money.of(12_000L)
        // 12% APR * 12 months = full year; 12,000 * 0.12 = 1,440
        val upfront = InterestCore.calculateFixedUpfrontInterest(principal, BigDecimal("0.12"), 12)
        assertThat(upfront.amount).isEqualByComparingTo("1440.00")
    }

    @Test
    fun `revolving minimum payment calculation`() {
        val principal = Money.of(5_000L)
        val interest = Money.of(75L)
        // minimum = 75 + (5000 * 1%) = 75 + 50 = 125
        val minimum = InterestCore.calculateRevolvingMinimumPayment(principal, interest)
        assertThat(minimum.amount).isEqualByComparingTo("125.00")
    }

    @Test
    fun `fixed monthly installment for 10000 at 24pct over 12 months`() {
        val principal = Money.of(10_000L)
        val installment = InterestCore.calculateFixedMonthlyInstallment(principal, annualRate24pct, 12)
        // Standard amortization: roughly $949.28
        assertThat(installment.amount).isBetween(BigDecimal("940.00"), BigDecimal("960.00"))
    }
}
