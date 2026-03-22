package com.lending.poc.domain.product

import com.lending.poc.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode

// ──────────────────────────────────────────────────────────────────────────────
// Extension properties for readable numeric literals in the DSL
// ──────────────────────────────────────────────────────────────────────────────

/** Converts a Double to a rate BigDecimal, e.g. `24.0.percent` → 0.240000 */
val Double.percent: BigDecimal
    get() = BigDecimal.valueOf(this).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)

/** Converts a Long to a USD Money amount, e.g. `1_000.usd` → Money($1000) */
val Long.usd: Money get() = Money.of(this)

/** Converts an Int to a USD Money amount, e.g. `25.usd` → Money($25) */
val Int.usd: Money get() = Money.of(this.toLong())

/** Converts an Int to number of days, e.g. `30.days` → 30 */
val Int.days: Int get() = this

// ──────────────────────────────────────────────────────────────────────────────
// DSL Marker
// ──────────────────────────────────────────────────────────────────────────────

@DslMarker
annotation class LendingProductDsl

// ──────────────────────────────────────────────────────────────────────────────
// DSL Builders
// ──────────────────────────────────────────────────────────────────────────────

@LendingProductDsl
class LendingProductBuilder(val id: ProductId, val name: String) {
    var description: String = ""
    private var interestBuilder = InterestConfigBuilder()
    private var paymentBuilder = PaymentConfigBuilder()
    private var hierarchyBuilder: PaymentHierarchyConfigBuilder? = null
    private var withdrawalBuilder = WithdrawalConfigBuilder()
    private var feeBuilder = FeeConfigBuilder()
    private var cooldownBuilder = CooldownConfigBuilder()

    fun interest(block: InterestConfigBuilder.() -> Unit) {
        interestBuilder = InterestConfigBuilder().apply(block)
    }

    fun payments(block: PaymentConfigBuilder.() -> Unit) {
        paymentBuilder = PaymentConfigBuilder().apply(block)
    }

    fun paymentHierarchy(block: PaymentHierarchyConfigBuilder.() -> Unit) {
        hierarchyBuilder = PaymentHierarchyConfigBuilder().apply(block)
    }

    fun withdrawal(block: WithdrawalConfigBuilder.() -> Unit) {
        withdrawalBuilder = WithdrawalConfigBuilder().apply(block)
    }

    fun fees(block: FeeConfigBuilder.() -> Unit) {
        feeBuilder = FeeConfigBuilder().apply(block)
    }

    fun cooldown(block: CooldownConfigBuilder.() -> Unit) {
        cooldownBuilder = CooldownConfigBuilder().apply(block)
    }

    fun build() = LendingProduct(
        id = id,
        name = name,
        description = description,
        interestConfig = interestBuilder.build(),
        paymentConfig = paymentBuilder.build(),
        paymentHierarchy = hierarchyBuilder?.build() ?: PaymentHierarchyConfig.DEFAULT,
        withdrawalConfig = withdrawalBuilder.build(),
        feeConfig = feeBuilder.build(),
        cooldownConfig = cooldownBuilder.build(),
    )
}

@LendingProductDsl
class InterestConfigBuilder {
    var type: InterestType = InterestType.DAILY_ACCRUAL
    var annualRate: BigDecimal = BigDecimal.ZERO
    var gracePeriodDays: Int = 0
    fun build() = InterestConfig(type, annualRate, gracePeriodDays)
}

@LendingProductDsl
class PaymentConfigBuilder {
    var cycle: PaymentCycle = PaymentCycle.MONTHLY
    var numberOfCycles: Int? = null
    var minimumPaymentRule: MinimumPaymentRule = MinimumPaymentRule.FIXED_INSTALLMENT
    var allowEarlyPayoff: Boolean = true
    private val loyaltyTierBuilders = mutableListOf<LoyaltyTierBuilder>()

    fun loyaltyTier(block: LoyaltyTierBuilder.() -> Unit) {
        loyaltyTierBuilders += LoyaltyTierBuilder().apply(block)
    }

    fun build() = PaymentConfig(
        cycle, numberOfCycles, minimumPaymentRule, allowEarlyPayoff,
        loyaltyTierBuilders.map { it.build() }.sortedBy { it.minPastLoans },
    )
}

@LendingProductDsl
class PaymentHierarchyConfigBuilder {
    private val slots = mutableListOf<PaymentSlotConfig>()

    fun slot(bucket: AllocationBucket) {
        slots += PaymentSlotConfig(bucket)
    }

    fun slot(
        bucket: AllocationBucket,
        companionBucket: AllocationBucket,
        strategy: AllocationStrategy = AllocationStrategy.SEQUENTIAL,
    ) {
        slots += PaymentSlotConfig(bucket, companionBucket, strategy)
    }

    fun build(): PaymentHierarchyConfig = PaymentHierarchyConfig(slots.toList())
}

@LendingProductDsl
class LoyaltyTierBuilder {
    var minPastLoans: Int = 0
    var cycleOverride: PaymentCycle = PaymentCycle.MONTHLY
    var numberOfCyclesOverride: Int? = null
    fun build() = LoyaltyTier(minPastLoans, cycleOverride, numberOfCyclesOverride)
}

@LendingProductDsl
class WithdrawalConfigBuilder {
    var type: WithdrawalType = WithdrawalType.SINGLE
    var minimumAmount: Money = Money.of(0L)
    var maximumAmount: Money = Money.of(0L)
    var drawFee: BigDecimal = BigDecimal.ZERO
    fun build() = WithdrawalConfig(type, minimumAmount, maximumAmount, drawFee)
}

@LendingProductDsl
class FeeConfigBuilder {
    var originationFee: BigDecimal = BigDecimal.ZERO
    var lateFee: Money = Money.ZERO
    var nsfFee: Money = Money.ZERO
    fun build() = FeeConfig(originationFee, lateFee, nsfFee)
}

@LendingProductDsl
class CooldownConfigBuilder {
    var afterPayoff: Int = 0
    fun build() = CooldownConfig(afterPayoff)
}

// ──────────────────────────────────────────────────────────────────────────────
// Top-level DSL entry-point function
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Creates a [LendingProduct] using a type-safe builder DSL.
 *
 * ```kotlin
 * val myProduct = lendingProduct(id = "INST_12M", name = "12-Month Installment Loan") {
 *     description = "Fixed monthly payments with daily interest accrual"
 *     interest {
 *         type = InterestType.DAILY_ACCRUAL
 *         annualRate = 24.0.percent
 *         gracePeriodDays = 3
 *     }
 *     payments {
 *         cycle = PaymentCycle.MONTHLY
 *         numberOfCycles = 12
 *     }
 *     withdrawal {
 *         type = WithdrawalType.SINGLE
 *         minimumAmount = 1_000.usd
 *         maximumAmount = 50_000.usd
 *     }
 *     fees {
 *         originationFee = 2.0.percent
 *         lateFee = 25.usd
 *     }
 *     cooldown {
 *         afterPayoff = 30.days
 *     }
 * }
 * ```
 */
fun lendingProduct(id: String, name: String, block: LendingProductBuilder.() -> Unit): LendingProduct =
    LendingProductBuilder(ProductId(id), name).apply(block).build()
