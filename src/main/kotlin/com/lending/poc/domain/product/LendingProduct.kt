package com.lending.poc.domain.product

import com.lending.poc.domain.model.*
import java.math.BigDecimal

data class LendingProduct(
    val id: ProductId,
    val name: String,
    val description: String,
    val interestConfig: InterestConfig,
    val paymentConfig: PaymentConfig,
    val paymentHierarchy: PaymentHierarchyConfig = PaymentHierarchyConfig.DEFAULT,
    val withdrawalConfig: WithdrawalConfig,
    val feeConfig: FeeConfig,
    val cooldownConfig: CooldownConfig,
)

data class InterestConfig(
    val type: InterestType,
    val annualRate: BigDecimal,
    val gracePeriodDays: Int = 0,
)

/**
 * How payments are ordered across allocation buckets, and how paired (base + companion) buckets
 * are settled relative to each other.
 *
 * @see PaymentSlotConfig
 * @see AllocationStrategy
 */
data class PaymentHierarchyConfig(val slots: List<PaymentSlotConfig>) {
    companion object {
        /**
         * Default hierarchy: fees first, then interest (no tax companion), then principal.
         * Preserves the original hardcoded behaviour.
         */
        val DEFAULT = PaymentHierarchyConfig(
            listOf(
                PaymentSlotConfig(AllocationBucket.FEE),
                PaymentSlotConfig(AllocationBucket.INTEREST),
                PaymentSlotConfig(AllocationBucket.PRINCIPAL),
            )
        )
    }
}

/**
 * One "slot" in the payment waterfall.
 *
 * @param bucket         The primary outstanding balance that this slot pays down.
 * @param companionBucket An optional secondary bucket settled alongside the primary one
 *                        (e.g., TAX_ON_INTEREST paired with INTEREST).
 * @param strategy       How to split the payment between [bucket] and [companionBucket]
 *                       when [companionBucket] is non-null.
 */
data class PaymentSlotConfig(
    val bucket: AllocationBucket,
    val companionBucket: AllocationBucket? = null,
    val strategy: AllocationStrategy = AllocationStrategy.SEQUENTIAL,
)

/**
 * A loyalty tier that overrides the default payment cycle when a borrower has accumulated
 * enough past loans.
 *
 * @param minPastLoans          Minimum number of previously completed loans to qualify.
 * @param cycleOverride         The payment cycle granted at this tier.
 * @param numberOfCyclesOverride The number of payment cycles (null = open-ended / revolving).
 */
data class LoyaltyTier(
    val minPastLoans: Int,
    val cycleOverride: PaymentCycle,
    val numberOfCyclesOverride: Int?,
)

data class PaymentConfig(
    val cycle: PaymentCycle,
    val numberOfCycles: Int?,
    val minimumPaymentRule: MinimumPaymentRule = MinimumPaymentRule.FIXED_INSTALLMENT,
    val allowEarlyPayoff: Boolean = true,
    /** Loyalty overrides, sorted ascending by [LoyaltyTier.minPastLoans]. */
    val loyaltyTiers: List<LoyaltyTier> = emptyList(),
) {
    /**
     * Returns the effective (cycle, numberOfCycles) pair for a borrower with [pastLoanCount]
     * previously completed loans.  The highest qualifying tier wins; falls back to the base
     * config when no tier matches.
     */
    fun resolvedFor(pastLoanCount: Int): Pair<PaymentCycle, Int?> {
        val tier = loyaltyTiers
            .filter { pastLoanCount >= it.minPastLoans }
            .maxByOrNull { it.minPastLoans }
        return tier?.let { it.cycleOverride to it.numberOfCyclesOverride }
            ?: (cycle to numberOfCycles)
    }
}

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
