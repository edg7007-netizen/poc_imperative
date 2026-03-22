package com.lending.poc.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

data class Money(val amount: BigDecimal, val currency: String = "USD") {
    companion object {
        val ZERO = Money(BigDecimal.ZERO.setScale(2))
        fun of(amount: Double, currency: String = "USD") =
            Money(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP), currency)
        fun of(amount: Long, currency: String = "USD") =
            Money(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP), currency)
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add different currencies" }
        return Money(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract different currencies" }
        return Money(amount - other.amount, currency)
    }

    operator fun times(factor: BigDecimal): Money =
        Money((amount * factor).setScale(2, RoundingMode.HALF_UP), currency)

    operator fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    fun isPositive() = amount > BigDecimal.ZERO
    fun isNegative() = amount < BigDecimal.ZERO
    fun isZero() = amount.compareTo(BigDecimal.ZERO) == 0

    override fun toString() = "$currency ${amount.toPlainString()}"
}
