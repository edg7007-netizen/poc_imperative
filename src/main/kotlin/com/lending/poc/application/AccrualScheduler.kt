package com.lending.poc.application

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled daily interest and late-fee accrual for all active loans.
 * Runs at 1:00 AM every day.
 */
@Component
class AccrualScheduler(private val loanApplicationService: LoanApplicationService) {

    private val log = LoggerFactory.getLogger(AccrualScheduler::class.java)

    @Scheduled(cron = "0 0 1 * * *")
    fun runDailyAccrual() {
        log.info("AccrualScheduler: starting daily accrual run")
        val activeLoans = loanApplicationService.findAllActiveLoans()
        log.info("AccrualScheduler: processing ${activeLoans.size} active loan(s)")

        activeLoans.forEach { loan ->
            try {
                loanApplicationService.runDailyAccrualForActiveLoan(loan.id)
                log.debug("Accrual completed for loan ${loan.id}")
            } catch (ex: Exception) {
                log.error("Accrual failed for loan ${loan.id}", ex)
            }
        }

        log.info("AccrualScheduler: daily accrual run complete")
    }
}
