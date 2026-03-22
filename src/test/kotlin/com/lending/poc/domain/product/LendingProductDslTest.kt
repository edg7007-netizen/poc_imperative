package com.lending.poc.domain.product

import com.lending.poc.domain.model.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LendingProductDslTest {

    @Test
    fun `personal installment loan is configured correctly via DSL`() {
        val product = ProductCatalog.PERSONAL_INSTALLMENT_LOAN

        assertThat(product.name).isEqualTo("Personal Installment Loan")
        assertThat(product.id).isEqualTo("PERSONAL_INSTALLMENT_LOAN")
        assertThat(product.description).isNotBlank()

        // Interest config
        assertThat(product.interestConfig.type).isEqualTo(InterestType.DAILY_ACCRUAL)
        assertThat(product.interestConfig.annualRate).isEqualByComparingTo("0.240000")
        assertThat(product.interestConfig.gracePeriodDays).isEqualTo(3)

        // Payment config
        assertThat(product.paymentConfig.cycle).isEqualTo(PaymentCycle.MONTHLY)
        assertThat(product.paymentConfig.numberOfCycles).isEqualTo(12)
        assertThat(product.paymentConfig.minimumPaymentRule).isEqualTo(MinimumPaymentRule.FIXED_INSTALLMENT)
        assertThat(product.paymentConfig.allowEarlyPayoff).isTrue()

        // Withdrawal config
        assertThat(product.withdrawalConfig.type).isEqualTo(WithdrawalType.SINGLE)
        assertThat(product.withdrawalConfig.minimumAmount).isEqualTo(Money.of(1_000L))
        assertThat(product.withdrawalConfig.maximumAmount).isEqualTo(Money.of(50_000L))

        // Fee config
        assertThat(product.feeConfig.originationFee).isEqualByComparingTo("0.020000")
        assertThat(product.feeConfig.lateFee).isEqualTo(Money.of(25L))
        assertThat(product.feeConfig.nsfFee).isEqualTo(Money.of(35L))

        // Cooldown config
        assertThat(product.cooldownConfig.afterPayoffDays).isEqualTo(30)
    }

    @Test
    fun `revolving line of credit is configured correctly via DSL`() {
        val product = ProductCatalog.REVOLVING_LINE_OF_CREDIT

        assertThat(product.name).isEqualTo("Revolving Line of Credit")
        assertThat(product.id).isEqualTo("REVOLVING_LINE_OF_CREDIT")

        // Interest config
        assertThat(product.interestConfig.type).isEqualTo(InterestType.DAILY_ACCRUAL)
        assertThat(product.interestConfig.annualRate).isEqualByComparingTo("0.180000")
        assertThat(product.interestConfig.gracePeriodDays).isEqualTo(0)

        // Payment config
        assertThat(product.paymentConfig.cycle).isEqualTo(PaymentCycle.MONTHLY)
        assertThat(product.paymentConfig.numberOfCycles).isNull()
        assertThat(product.paymentConfig.minimumPaymentRule).isEqualTo(MinimumPaymentRule.INTEREST_PLUS_ONE_PERCENT)

        // Withdrawal config
        assertThat(product.withdrawalConfig.type).isEqualTo(WithdrawalType.MULTIPLE)
        assertThat(product.withdrawalConfig.minimumAmount).isEqualTo(Money.of(500L))
        assertThat(product.withdrawalConfig.maximumAmount).isEqualTo(Money.of(25_000L))
        assertThat(product.withdrawalConfig.drawFee).isEqualByComparingTo("0.030000")

        // Fee config
        assertThat(product.feeConfig.originationFee).isEqualByComparingTo("0.000000")
        assertThat(product.feeConfig.lateFee).isEqualTo(Money.of(35L))

        // Cooldown config
        assertThat(product.cooldownConfig.afterPayoffDays).isEqualTo(0)
    }

    @Test
    fun `DSL extension properties convert numeric literals correctly`() {
        assertThat(24.0.percent).isEqualByComparingTo("0.240000")
        assertThat(18.0.percent).isEqualByComparingTo("0.180000")
        assertThat(2.0.percent).isEqualByComparingTo("0.020000")
        assertThat(0.0.percent).isEqualByComparingTo("0.000000")

        assertThat(25.usd).isEqualTo(Money.of(25L))
        assertThat(1_000.usd).isEqualTo(Money.of(1_000L))
        assertThat(50_000.usd).isEqualTo(Money.of(50_000L))

        assertThat(30.days).isEqualTo(30)
        assertThat(0.days).isEqualTo(0)
    }

    @Test
    fun `product catalog returns correct products by ID`() {
        assertThat(ProductCatalog.findById("PERSONAL_INSTALLMENT_LOAN"))
            .isEqualTo(ProductCatalog.PERSONAL_INSTALLMENT_LOAN)
        assertThat(ProductCatalog.findById("REVOLVING_LINE_OF_CREDIT"))
            .isEqualTo(ProductCatalog.REVOLVING_LINE_OF_CREDIT)
        assertThat(ProductCatalog.findById("NONEXISTENT")).isNull()
    }

    @Test
    fun `DSL builder produces identical product when called twice`() {
        val p1 = lendingProduct(id = "TEST", name = "Test Product") {
            description = "Test"
            interest { annualRate = 12.0.percent }
            fees { lateFee = 10.usd }
            cooldown { afterPayoff = 7.days }
        }
        val p2 = lendingProduct(id = "TEST", name = "Test Product") {
            description = "Test"
            interest { annualRate = 12.0.percent }
            fees { lateFee = 10.usd }
            cooldown { afterPayoff = 7.days }
        }
        assertThat(p1).isEqualTo(p2)
    }
}
