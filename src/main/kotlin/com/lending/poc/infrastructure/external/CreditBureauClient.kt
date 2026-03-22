package com.lending.poc.infrastructure.external

import com.lending.poc.domain.model.BorrowerId
import com.lending.poc.domain.model.Money
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal

/**
 * HTTP client for the external Credit Bureau scoring service.
 *
 * Active only when [external.credit-bureau.enabled] = true (the default when the application
 * runs with Docker Compose). Disabled in tests via src/test/resources/application.yml to keep
 * unit tests fully in-memory.
 *
 * Locally the credit bureau is stubbed by WireMock (see wiremock/mappings/credit-bureau.json).
 */
@Component
@ConditionalOnProperty(name = ["external.credit-bureau.enabled"], havingValue = "true")
class CreditBureauClient(
    @Value("\${external.credit-bureau.base-url}") baseUrl: String,
) {
    private val log = LoggerFactory.getLogger(CreditBureauClient::class.java)
    private val restClient = RestClient.create(baseUrl)

    data class CreditScoreRequest(val borrowerId: String, val requestedAmount: BigDecimal)
    data class CreditScoreResponse(
        val borrowerId: String,
        val score: Int,
        val eligible: Boolean,
        val maxEligibleAmount: BigDecimal,
        val currency: String,
    )

    /**
     * Requests a credit score for the given borrower and requested loan amount.
     *
     * @return the credit bureau response, or a permissive fallback if the service is unreachable.
     */
    fun checkEligibility(borrowerId: BorrowerId, requestedAmount: Money): CreditScoreResponse {
        return try {
            val response = restClient.post()
                .uri("/credit-bureau/score")
                .body(CreditScoreRequest(borrowerId.value, requestedAmount.amount))
                .retrieve()
                .body(CreditScoreResponse::class.java)!!
            log.info("Credit bureau: borrower=${borrowerId.value} score=${response.score} eligible=${response.eligible}")
            response
        } catch (ex: Exception) {
            log.warn("Credit bureau unreachable — defaulting to eligible: {}", ex.message)
            CreditScoreResponse(borrowerId.value, 700, true, requestedAmount.amount, requestedAmount.currency)
        }
    }
}
