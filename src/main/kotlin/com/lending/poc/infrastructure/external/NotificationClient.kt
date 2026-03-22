package com.lending.poc.infrastructure.external

import com.lending.poc.domain.model.BorrowerId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * HTTP client for the external Notification delivery service.
 *
 * Active only when [external.notifications.enabled] = true. Disabled in tests.
 *
 * Locally the notification service is stubbed by WireMock (see wiremock/mappings/notifications.json).
 */
@Component
@ConditionalOnProperty(name = ["external.notifications.enabled"], havingValue = "true")
class NotificationClient(
    @Value("\${external.notifications.base-url}") baseUrl: String,
) {
    private val log = LoggerFactory.getLogger(NotificationClient::class.java)
    private val restClient = RestClient.create(baseUrl)

    data class NotificationRequest(val recipientId: String, val message: String)
    data class NotificationResponse(val delivered: Boolean, val messageId: String?, val recipientId: String?)

    /**
     * Delivers a notification message to the given recipient.
     * Failures are caught and logged so that a notification issue never aborts a loan transaction.
     */
    fun send(recipientId: BorrowerId, message: String) {
        try {
            val response = restClient.post()
                .uri("/notifications/send")
                .body(NotificationRequest(recipientId.value, message))
                .retrieve()
                .body(NotificationResponse::class.java)
            log.info("Notification delivered=${response?.delivered} messageId=${response?.messageId} recipient=${recipientId.value}")
        } catch (ex: Exception) {
            log.warn("Notification service unreachable — notification not sent: {}", ex.message)
        }
    }
}
