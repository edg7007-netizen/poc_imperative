package com.lending.poc.domain.model

/**
 * Strongly-typed product identifier. Prevents mix-ups with borrower IDs or other String fields.
 */
@JvmInline
value class ProductId(val value: String) {
    init { require(value.isNotBlank()) { "ProductId must not be blank" } }
    override fun toString() = value
}
