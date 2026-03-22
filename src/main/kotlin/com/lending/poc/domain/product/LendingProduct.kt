package com.lending.poc.domain.product

import com.lending.poc.domain.model.*
import java.math.BigDecimal

data class LendingProduct(
    val id: ProductId,
    val name: String,
    val description: String,
    val interestConfig: InterestConfig,
    val paymentConfig: PaymentConfig,
    val withdrawalConfig: WithdrawalConfig,
    val feeConfig: FeeConfig,
    val cooldownConfig: CooldownConfig,
)

data class InterestConfig(
    val type: InterestType,
    val annualRate: BigDecimal,
    val gracePeriodDays: Int = 0,
)

data class PaymentConfig(
    val cycle: PaymentCycle,
    val numberOfCycles: Int?,
    val minimumPaymentRule: MinimumPaymentRule = MinimumPaymentRule.FIXED_INSTALLMENT,
    val allowEarlyPayoff: Boolean = true,
)

data class WithdrawalConfig(
    val type: WithdrawalType,
    val minimumAmount: Money,
    val maximumAmount: Money,
    val drawFee: BigDecimal = BigDecimal.ZERO,
)

data class FeeConfig(
    val originationFee: BigDecimal,
    val lateFee: Money,
    val nsfFee: Money,
)

data class CooldownConfig(
    val afterPayoffDays: Int,
)
