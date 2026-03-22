package com.lending.poc.domain.model

/**
 * Strongly-typed borrower identity. Prevents confusion with other String identifiers.
 */
@JvmInline
value class BorrowerId(val value: String) {
    init { require(value.isNotBlank()) { "BorrowerId must not be blank" } }
    override fun toString() = value
}
