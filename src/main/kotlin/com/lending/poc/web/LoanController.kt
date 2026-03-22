package com.lending.poc.web

import com.lending.poc.application.LoanApplicationService
import com.lending.poc.domain.model.BorrowerId
import com.lending.poc.domain.model.Money
import com.lending.poc.domain.model.ProductId
import com.lending.poc.web.dto.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/loans")
class LoanController(private val loanApplicationService: LoanApplicationService) {

    /** Apply for a new loan. Returns the loan in APPROVED status. */
    @PostMapping
    fun applyForLoan(@RequestBody request: ApplyForLoanRequest): ResponseEntity<LoanResponse> {
        val amount = Money.of(request.requestedAmount.toDouble(), request.currency)
        val loan = loanApplicationService.applyForLoan(
            productId = ProductId(request.productId),
            borrowerId = BorrowerId(request.borrowerId),
            requestedAmount = amount,
        )
        return ResponseEntity.ok(LoanResponse.from(loan))
    }

    /** Disburse an approved loan, transitioning it to ACTIVE. */
    @PostMapping("/{id}/disburse")
    fun disburseLoan(
        @PathVariable id: UUID,
        @RequestBody request: DisburseRequest,
    ): ResponseEntity<LoanResponse> {
        val amount = Money.of(request.amount.toDouble(), request.currency)
        val loan = loanApplicationService.disburseLoan(id, amount)
        return ResponseEntity.ok(LoanResponse.from(loan))
    }

    /** Cancel an approved (not yet disbursed) loan. */
    @PostMapping("/{id}/cancel")
    fun cancelLoan(@PathVariable id: UUID): ResponseEntity<LoanResponse> {
        val loan = loanApplicationService.cancelLoan(id)
        return ResponseEntity.ok(LoanResponse.from(loan))
    }

    /** Make a payment on an active loan. */
    @PostMapping("/{id}/payments")
    fun processPayment(
        @PathVariable id: UUID,
        @RequestBody request: PaymentRequest,
    ): ResponseEntity<LoanResponse> {
        val amount = Money.of(request.amount.toDouble(), request.currency)
        val loan = loanApplicationService.processPayment(id, amount)
        return ResponseEntity.ok(LoanResponse.from(loan))
    }

    /** Withdraw from a revolving credit line. */
    @PostMapping("/{id}/withdrawals")
    fun withdraw(
        @PathVariable id: UUID,
        @RequestBody request: WithdrawalRequest,
    ): ResponseEntity<LoanResponse> {
        val amount = Money.of(request.amount.toDouble(), request.currency)
        val loan = loanApplicationService.withdrawFromCreditLine(id, amount)
        return ResponseEntity.ok(LoanResponse.from(loan))
    }

    /** Retrieve loan details. */
    @GetMapping("/{id}")
    fun getLoan(@PathVariable id: UUID): ResponseEntity<LoanResponse> {
        val loan = loanApplicationService.getLoan(id)
        return ResponseEntity.ok(LoanResponse.from(loan))
    }

    /** Retrieve all ledger entries for a loan. */
    @GetMapping("/{id}/ledger")
    fun getLedger(@PathVariable id: UUID): ResponseEntity<List<LedgerEntryResponse>> {
        val entries = loanApplicationService.getLedger(id).map { LedgerEntryResponse.from(it) }
        return ResponseEntity.ok(entries)
    }
}
