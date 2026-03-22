package com.lending.poc.domain.core

import com.lending.poc.domain.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class FeeCoreTest {

    private val today = LocalDate.of(2024, 6, 15)

    // ── originationFee ───────────────────────────────────────────────────────

    @Test
    fun `origination fee is correctly calculated as percentage of principal`() {
        val principal = Money.of(10_000L)
        val twoPercent = BigDecimal("0.02")

        val fee = FeeCore.calculateOriginationFee(principal, twoPercent)
        assertThat(fee).isEqualTo(Money.of(200L))
    }

    @Test
    fun `origination fee is zero when rate is zero`() {
        val fee = FeeCore.calculateOriginationFee(Money.of(10_000L), BigDecimal.ZERO)
        assertThat(fee.isZero()).isTrue()
    }

    @Test
    fun `origination fee rounds correctly for fractional results`() {
        val principal = Money.of(10_001L)
        val twoPercent = BigDecimal("0.02")

        val fee = FeeCore.calculateOriginationFee(principal, twoPercent)
        // 10,001 * 0.02 = 200.02
        assertThat(fee.amount).isEqualByComparingTo("200.02")
    }

    // ── drawFee ──────────────────────────────────────────────────────────────

    @Test
    fun `draw fee is correctly calculated as percentage of drawn amount`() {
        val drawnAmount = Money.of(3_000L)
        val threePercent = BigDecimal("0.03")

        val fee = FeeCore.calculateDrawFee(drawnAmount, threePercent)
        assertThat(fee).isEqualTo(Money.of(90L))
    }

    @Test
    fun `draw fee is zero when rate is zero`() {
        val fee = FeeCore.calculateDrawFee(Money.of(5_000L), BigDecimal.ZERO)
        assertThat(fee.isZero()).isTrue()
    }

    // ── lateFee ──────────────────────────────────────────────────────────────

    @Test
    fun `late fee is not applicable on the due date itself`() {
        val dueDate = today
        val applicable = FeeCore.isLateFeeApplicable(dueDate, gracePeriodDays = 3, asOf = today)
        assertThat(applicable).isFalse()
    }

    @Test
    fun `late fee is not applicable within grace period`() {
        val dueDate = today.minusDays(2)
        val applicable = FeeCore.isLateFeeApplicable(dueDate, gracePeriodDays = 3, asOf = today)
        assertThat(applicable).isFalse()
    }

    @Test
    fun `late fee is not applicable on last day of grace period`() {
        val dueDate = today.minusDays(3)
        // today = dueDate + 3 → still within/at end of grace period (isAfter = false)
        val applicable = FeeCore.isLateFeeApplicable(dueDate, gracePeriodDays = 3, asOf = today)
        assertThat(applicable).isFalse()
    }

    @Test
    fun `late fee is applicable after grace period expires`() {
        val dueDate = today.minusDays(4) // 4 days past due
        val applicable = FeeCore.isLateFeeApplicable(dueDate, gracePeriodDays = 3, asOf = today)
        assertThat(applicable).isTrue()
    }

    @Test
    fun `late fee is applied only after grace period`() {
        val dueDate = today.minusDays(4)
        val lateFeeAmount = Money.of(25L)

        val fee = FeeCore.calculateLateFee(dueDate, gracePeriodDays = 3, lateFeeAmount, asOf = today)
        assertThat(fee).isEqualTo(lateFeeAmount)
    }

    @Test
    fun `late fee is not applied when null due date`() {
        val fee = FeeCore.calculateLateFee(null, gracePeriodDays = 3, Money.of(25L), asOf = today)
        assertThat(fee.isZero()).isTrue()
    }

    @Test
    fun `late fee with zero grace period applies immediately after due date`() {
        val dueDate = today.minusDays(1)
        val applicable = FeeCore.isLateFeeApplicable(dueDate, gracePeriodDays = 0, asOf = today)
        assertThat(applicable).isTrue()
    }
}
