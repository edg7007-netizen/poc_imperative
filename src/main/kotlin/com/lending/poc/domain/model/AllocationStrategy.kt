package com.lending.poc.domain.model

/**
 * Controls how a payment is split between a primary bucket and its companion bucket
 * within a single [com.lending.poc.domain.product.PaymentSlotConfig].
 *
 * Only relevant when [com.lending.poc.domain.product.PaymentSlotConfig.companionBucket] is set.
 */
enum class AllocationStrategy {
    /**
     * Clear the primary bucket completely before applying any remainder to the companion bucket.
     * Example: pay all outstanding interest first, then pay tax on interest.
     */
    SEQUENTIAL,

    /**
     * Split the available payment proportionally between the primary bucket and the companion bucket
     * based on their respective outstanding balances.
     * Example: pay interest and tax-on-interest simultaneously, in proportion to their balances.
     */
    PROPORTIONAL,
}
