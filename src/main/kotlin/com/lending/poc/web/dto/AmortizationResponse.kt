package com.lending.poc.web.dto

import com.lending.poc.domain.model.AmortizationRow
import java.math.BigDecimal
import java.time.LocalDate

data class AmortizationRowResponse(
    val period: Int,
    val paymentDate: LocalDate,
    val openingBalance: BigDecimal,
    val scheduledPayment: BigDecimal,
    val principalComponent: BigDecimal,
    val interestComponent: BigDecimal,
    val closingBalance: BigDecimal,
    val currency: String,
) {
    companion object {
        fun from(row: AmortizationRow) = AmortizationRowResponse(
            period = row.period,
            paymentDate = row.paymentDate,
            openingBalance = row.openingBalance.amount,
            scheduledPayment = row.scheduledPayment.amount,
            principalComponent = row.principalComponent.amount,
            interestComponent = row.interestComponent.amount,
            closingBalance = row.closingBalance.amount,
            currency = row.openingBalance.currency,
        )
    }
}
