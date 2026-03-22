package com.lending.poc.domain.product

import com.lending.poc.domain.model.*
import org.springframework.stereotype.Component

/**
 * Catalog holding all available lending products, defined via the DSL.
 * Static product constants are accessible via the companion object.
 */
@Component
class ProductCatalog {

    fun findById(id: ProductId): LendingProduct? = ALL_PRODUCTS[id.value]

    fun all(): List<LendingProduct> = ALL_PRODUCTS.values.toList()

    companion object {

        val PERSONAL_INSTALLMENT_LOAN: LendingProduct = lendingProduct(
            id = "PERSONAL_INSTALLMENT_LOAN",
            name = "Personal Installment Loan",
        ) {
            description = "Fixed-term personal loan with equal monthly payments"

            interest {
                type = InterestType.DAILY_ACCRUAL
                annualRate = 24.0.percent
                gracePeriodDays = 3
            }

            payments {
                cycle = PaymentCycle.MONTHLY
                numberOfCycles = 12
                minimumPaymentRule = MinimumPaymentRule.FIXED_INSTALLMENT
                allowEarlyPayoff = true
            }

            withdrawal {
                type = WithdrawalType.SINGLE
                minimumAmount = 1_000.usd
                maximumAmount = 50_000.usd
            }

            fees {
                originationFee = 2.0.percent
                lateFee = 25.usd
                nsfFee = 35.usd
            }

            cooldown {
                afterPayoff = 30.days
            }
        }

        val REVOLVING_LINE_OF_CREDIT: LendingProduct = lendingProduct(
            id = "REVOLVING_LINE_OF_CREDIT",
            name = "Revolving Line of Credit",
        ) {
            description = "Flexible credit line with multiple drawdowns"

            interest {
                type = InterestType.DAILY_ACCRUAL
                annualRate = 18.0.percent
                gracePeriodDays = 0
            }

            payments {
                cycle = PaymentCycle.MONTHLY
                numberOfCycles = null
                minimumPaymentRule = MinimumPaymentRule.INTEREST_PLUS_ONE_PERCENT
                allowEarlyPayoff = true
            }

            withdrawal {
                type = WithdrawalType.MULTIPLE
                minimumAmount = 500.usd
                maximumAmount = 25_000.usd
                drawFee = 3.0.percent
            }

            fees {
                originationFee = 0.0.percent
                lateFee = 35.usd
                nsfFee = 35.usd
            }

            cooldown {
                afterPayoff = 0.days
            }
        }

        private val ALL_PRODUCTS: Map<String, LendingProduct> = listOf(
            PERSONAL_INSTALLMENT_LOAN,
            REVOLVING_LINE_OF_CREDIT,
        ).associateBy { it.id.value }
    }
}

