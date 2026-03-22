# Lending Engine POC — Imperative Shell / Functional Core

A Kotlin + Spring Boot 3.4 proof-of-concept for a lending engine that demonstrates clean architecture through the **Functional Core / Imperative Shell** pattern.

---

## Table of Contents

1. [Project Purpose](#1-project-purpose)
2. [Architecture Overview](#2-architecture-overview)
3. [Package Structure](#3-package-structure)
4. [Domain Events & Outbox Transactionality](#4-domain-events--outbox-transactionality)
5. [Product DSL](#5-product-dsl)
6. [Payment Hierarchy](#6-payment-hierarchy)
7. [Loyalty Tiers](#7-loyalty-tiers)
8. [Amortization Schedules](#8-amortization-schedules)
9. [Running the Application](#9-running-the-application)
10. [REST API Reference](#10-rest-api-reference)
11. [Architecture Decisions](#11-architecture-decisions)

---

## 1. Project Purpose

This POC implements a simplified lending engine that handles the full loan lifecycle:

- **Loan approval** — validates requested amount against product limits
- **Disbursement** — transitions approved loans to active, calculates origination fees
- **Daily interest accrual** — accumulates interest on outstanding principal
- **Late fee assessment** — applies late fees after the grace period expires
- **Payment processing** — allocates payments across balances following a configurable waterfall
- **Revolving credit lines** — supports multiple drawdowns up to a credit limit
- **Amortization schedules** — generates period-by-period payment schedules for fixed-term loans

The engine is designed to be **purely deterministic in its domain logic**: every core function takes current state and input, and returns new state plus a list of effects to execute — with no side effects inside the domain layer.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Imperative Shell                         │
│  LoanController → LoanApplicationService → EffectExecutor   │
│                         (I/O lives here)                     │
└────────────────────────┬────────────────────────────────────┘
                         │ calls
┌────────────────────────▼────────────────────────────────────┐
│                    Functional Core                           │
│  LoanLifecycleCore, PaymentCore, InterestCore, FeeCore,      │
│  CooldownCore  (pure functions, zero I/O)                    │
└─────────────────────────────────────────────────────────────┘
```

**Functional Core** — all domain logic lives here. Functions return `(newState, List<LoanEffect>)` pairs; they never write to a database or emit events directly.

**Imperative Shell** — the application service loads state from the DB, calls the core, then executes the returned `LoanEffect` list via `EffectExecutor`. All I/O (persistence, event emission, notifications) happens here, within a single Spring `@Transactional` boundary.

---

## 3. Package Structure

```
com.lending.poc
├── application/
│   ├── LoanApplicationService.kt   — Orchestrates lifecycle ops; imperative shell
│   └── AccrualScheduler.kt         — Scheduled job for daily interest accrual
│
├── domain/
│   ├── core/
│   │   ├── LoanLifecycleCore.kt    — Loan state machine (approve, disburse, pay, cancel…)
│   │   ├── PaymentCore.kt          — Configurable payment waterfall allocation
│   │   ├── InterestCore.kt         — Interest calculations + amortization schedule
│   │   ├── FeeCore.kt              — Origination, draw, and late fee calculations
│   │   ├── CooldownCore.kt         — Post-payoff cooldown eligibility
│   │   ├── LoanEffect.kt           — Sealed class hierarchy of side-effect descriptors
│   │   └── LoanEventType.kt        — Domain event type enum
│   │
│   ├── model/
│   │   ├── Loan.kt                 — Loan aggregate (immutable data class)
│   │   ├── Money.kt                — Value object for monetary amounts + currency
│   │   ├── AmortizationRow.kt      — One period in an amortization schedule
│   │   ├── AllocationBucket.kt     — Enum: FEE, INTEREST, TAX_ON_INTEREST, PRINCIPAL
│   │   ├── AllocationStrategy.kt   — Enum: SEQUENTIAL, PROPORTIONAL
│   │   ├── LedgerEntry.kt          — Immutable ledger record
│   │   ├── LoanStatus.kt           — Loan lifecycle status enum
│   │   ├── LedgerEntryType.kt      — Ledger entry type enum
│   │   ├── PaymentCycle.kt         — MONTHLY, WEEKLY, BIWEEKLY, SINGLE
│   │   ├── InterestType.kt         — DAILY_ACCRUAL, FIXED_UPFRONT
│   │   ├── MinimumPaymentRule.kt   — FIXED_INSTALLMENT, INTEREST_PLUS_ONE_PERCENT
│   │   ├── WithdrawalType.kt       — SINGLE, MULTIPLE
│   │   ├── BorrowerId.kt           — Typed wrapper for borrower identifier
│   │   └── ProductId.kt            — Typed wrapper for product identifier
│   │
│   └── product/
│       ├── LendingProduct.kt       — Product configuration data classes
│       ├── LendingProductDsl.kt    — Type-safe Kotlin DSL for defining products
│       └── ProductCatalog.kt       — Registry of all available lending products
│
├── infrastructure/
│   ├── effect/
│   │   └── EffectExecutor.kt       — Executes LoanEffect list (persist, emit events…)
│   ├── outbox/
│   │   ├── LoanDomainEvent.kt      — JPA entity for the outbox table
│   │   ├── OutboxEventWriter.kt    — Writes events to outbox within transaction
│   │   └── OutboxProcessor.kt      — Polls outbox and publishes ApplicationEvents
│   └── persistence/
│       ├── entity/                 — JPA entities (LoanEntity, LedgerEntryEntity…)
│       ├── mapper/                 — Domain ↔ entity mappers
│       └── repository/             — Spring Data JPA repositories
│
└── web/
    ├── LoanController.kt           — REST endpoints
    └── dto/
        ├── LoanRequest.kt          — Request DTOs
        ├── LoanResponse.kt         — Loan response DTO
        └── AmortizationResponse.kt — Amortization row response DTO
```

---

## 4. Domain Events & Outbox Transactionality

Domain events are emitted via the **Transactional Outbox Pattern** to guarantee at-least-once delivery without distributed transactions:

1. **`LoanLifecycleCore`** returns `LoanEffect.EmitEvent(eventType, aggregateId, payload)` alongside state changes — no I/O performed.

2. **`EffectExecutor`** processes each effect in the same Spring `@Transactional` context:
   - `LoanEffect.PersistLoan` → saves the loan entity
   - `LoanEffect.PersistLedgerEntry` → saves ledger entries
   - `LoanEffect.EmitEvent` → writes a row to the `outbox_events` table via `OutboxEventWriter`
   - `LoanEffect.SendNotification` → (currently logged; extensible hook for real channels)

3. **`OutboxProcessor`** polls the outbox table periodically and re-publishes unprocessed rows as Spring `ApplicationEvent`s, marking them delivered. If the transaction in step 2 rolls back, no outbox row is written, so no ghost events are emitted.

This pattern ensures that **loan state changes and their corresponding events are always committed atomically**.

---

## 5. Product DSL

Products are defined using a type-safe Kotlin builder DSL, making them readable and refactor-safe:

```kotlin
val myProduct = lendingProduct(id = "INST_12M", name = "12-Month Installment Loan") {
    description = "Fixed monthly payments with daily interest accrual"

    interest {
        type = InterestType.DAILY_ACCRUAL
        annualRate = 24.0.percent      // extension property: Double → BigDecimal
        gracePeriodDays = 3
    }

    payments {
        cycle = PaymentCycle.MONTHLY
        numberOfCycles = 12
        minimumPaymentRule = MinimumPaymentRule.FIXED_INSTALLMENT
        allowEarlyPayoff = true

        loyaltyTier {
            minPastLoans = 1
            cycleOverride = PaymentCycle.MONTHLY
            numberOfCyclesOverride = 18     // repeat customers get 18 months
        }
        loyaltyTier {
            minPastLoans = 3
            cycleOverride = PaymentCycle.MONTHLY
            numberOfCyclesOverride = 24     // long-term customers get 24 months
        }
    }

    paymentHierarchy {
        slot(AllocationBucket.FEE)
        slot(AllocationBucket.INTEREST, AllocationBucket.TAX_ON_INTEREST, AllocationStrategy.PROPORTIONAL)
        slot(AllocationBucket.PRINCIPAL)
    }

    withdrawal {
        type = WithdrawalType.SINGLE
        minimumAmount = 1_000.usd          // extension property: Long → Money
        maximumAmount = 50_000.usd
    }

    fees {
        originationFee = 2.0.percent
        lateFee = 25.usd
        nsfFee = 35.usd
    }

    cooldown {
        afterPayoff = 30.days              // extension property: Int → Int (readable)
    }
}
```

### DSL Extension Properties

| Expression       | Result                             |
|------------------|------------------------------------|
| `24.0.percent`   | `BigDecimal("0.240000")`           |
| `1_000.usd`      | `Money(1000.00, "USD")`            |
| `30.days`        | `30` (Int, documents intent)       |

All products are registered in `ProductCatalog` and can be looked up by `ProductId`.

---

## 6. Payment Hierarchy

The payment waterfall determines how an incoming payment is distributed across outstanding balances. Each product defines its own `PaymentHierarchyConfig` via the DSL.

### Concepts

| Type | Description |
|------|-------------|
| `AllocationBucket` | Which balance a slot targets: `FEE`, `INTEREST`, `TAX_ON_INTEREST`, `PRINCIPAL` |
| `AllocationStrategy` | How to split between a primary and companion bucket |
| `PaymentSlotConfig` | One waterfall step: primary bucket + optional companion + strategy |
| `PaymentHierarchyConfig` | Ordered list of `PaymentSlotConfig` slots |

### Allocation Strategies

**`SEQUENTIAL`** — clear the primary bucket entirely before touching the companion:
```
Available: $120   Interest: $100   TaxOnInterest: $30
→ Pay $100 to Interest, then $20 to TaxOnInterest
```

**`PROPORTIONAL`** — split in proportion to each bucket's outstanding balance:
```
Available: $60   Interest: $100   TaxOnInterest: $20  (total: $120)
→ Interest ratio = 100/120 ≈ 0.833
→ Pay $50 to Interest, $10 to TaxOnInterest
```

### Default Hierarchy

If no `paymentHierarchy` block is specified, the default applies:
```
FEE → INTEREST → PRINCIPAL
```

### Example: Personal Installment Loan

```kotlin
paymentHierarchy {
    slot(AllocationBucket.FEE)
    slot(AllocationBucket.INTEREST, AllocationBucket.TAX_ON_INTEREST, AllocationStrategy.PROPORTIONAL)
    slot(AllocationBucket.PRINCIPAL)
}
```
Fees are cleared first, then interest and tax-on-interest are settled proportionally, then principal.

---

## 7. Loyalty Tiers

A `LoyaltyTier` overrides the default payment term for borrowers who have completed enough previous loans. This allows you to reward repeat borrowers with longer repayment periods automatically.

```kotlin
payments {
    cycle = PaymentCycle.MONTHLY
    numberOfCycles = 12           // default: 12 months

    loyaltyTier {
        minPastLoans = 1          // ≥1 completed loans → 18 months
        cycleOverride = PaymentCycle.MONTHLY
        numberOfCyclesOverride = 18
    }
    loyaltyTier {
        minPastLoans = 3          // ≥3 completed loans → 24 months
        cycleOverride = PaymentCycle.MONTHLY
        numberOfCyclesOverride = 24
    }
}
```

### Resolution Logic

The highest qualifying tier wins (most `minPastLoans` that the borrower meets). If no tier qualifies, the base `(cycle, numberOfCycles)` is used.

```kotlin
paymentConfig.resolvedFor(pastLoanCount = 2)
// → (MONTHLY, 18)   — first tier qualifies (minPastLoans=1), second doesn't (needs 3)

paymentConfig.resolvedFor(pastLoanCount = 5)
// → (MONTHLY, 24)   — second tier wins (minPastLoans=3)

paymentConfig.resolvedFor(pastLoanCount = 0)
// → (MONTHLY, 12)   — no tier qualifies; base config applies
```

Pass `pastLoanCount` in the loan application request to activate tier resolution:

```json
POST /api/loans
{
  "productId": "PERSONAL_INSTALLMENT_LOAN",
  "borrowerId": "borrower-42",
  "requestedAmount": 10000,
  "pastLoanCount": 3
}
```

---

## 8. Amortization Schedules

For fixed-term loans, the engine can generate a full period-by-period amortization schedule using the standard annuity formula:

```
M = P × [r(1+r)^n] / [(1+r)^n − 1]
```

where `P` = principal, `r` = monthly rate, `n` = number of payments.

Each `AmortizationRow` contains:

| Field | Description |
|-------|-------------|
| `period` | Payment number (1 = first payment) |
| `paymentDate` | Scheduled date of this payment |
| `openingBalance` | Principal at the start of the period |
| `scheduledPayment` | Total payment amount due |
| `interestComponent` | Portion covering interest |
| `principalComponent` | Portion reducing the principal |
| `closingBalance` | Principal remaining after payment |

The last period is adjusted to pay off the exact remaining balance, eliminating rounding residuals.

**Retrieve via REST:**
```
GET /api/loans/{id}/amortization
```

**Example response:**
```json
[
  {
    "period": 1,
    "paymentDate": "2024-02-15",
    "openingBalance": 10000.00,
    "scheduledPayment": 943.56,
    "interestComponent": 200.00,
    "principalComponent": 743.56,
    "closingBalance": 9256.44,
    "currency": "USD"
  },
  ...
]
```

---

## 9. Running the Application

### Prerequisites

- JDK 17+
- Gradle (wrapper included)

### Start

```bash
cd /path/to/poc_imperative
./gradlew bootRun
```

The application starts on `http://localhost:8080`. The H2 in-memory database is configured with `ddl-auto: create-drop`, so Hibernate auto-creates the schema from JPA entities on startup.

### Run Tests

```bash
./gradlew test
```

### Build

```bash
./gradlew build
```

---

## 10. REST API Reference

Base path: `/api/loans`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/loans` | Apply for a new loan |
| `POST` | `/api/loans/{id}/disburse` | Disburse an approved loan |
| `POST` | `/api/loans/{id}/cancel` | Cancel an approved loan |
| `POST` | `/api/loans/{id}/payments` | Make a payment on an active loan |
| `POST` | `/api/loans/{id}/withdrawals` | Draw from a revolving credit line |
| `GET`  | `/api/loans/{id}` | Get loan details |
| `GET`  | `/api/loans/{id}/ledger` | Get all ledger entries |
| `GET`  | `/api/loans/{id}/amortization` | Get amortization schedule |

### Apply for Loan

```http
POST /api/loans
Content-Type: application/json

{
  "productId": "PERSONAL_INSTALLMENT_LOAN",
  "borrowerId": "borrower-1",
  "requestedAmount": 10000,
  "currency": "USD",
  "pastLoanCount": 0
}
```

### Make a Payment

```http
POST /api/loans/{id}/payments
Content-Type: application/json

{
  "amount": 943.56,
  "currency": "USD"
}
```

### Disburse a Loan

```http
POST /api/loans/{id}/disburse
Content-Type: application/json

{
  "amount": 10000,
  "currency": "USD"
}
```

---

## 11. Architecture Decisions

### Why Functional Core / Imperative Shell?

Domain functions that accept state and return `(newState, effects)` are:
- **Trivially unit-testable** — no mocking of databases or event buses needed
- **Deterministic** — given the same inputs, always produce the same outputs
- **Composable** — effects can be inspected, reordered, filtered in tests

The imperative shell is thin: load state → call pure function → execute effects.

### Why Kotlin Data Classes for Domain Objects?

Kotlin data classes provide structural equality, `copy()`, and immutability by convention. `Loan` is an immutable data class — every state transition produces a new instance. This prevents accidental mutation and makes transitions explicit and traceable.

### Why Sealed `LoanEffect`?

`LoanEffect` is a sealed class, making the set of possible side effects closed and exhaustive. Callers (the imperative shell) can pattern-match on all possible effects at compile time, and adding a new effect type forces updates throughout the codebase.

### Why a Product DSL instead of YAML/database config?

Product definitions are code, not data. A type-safe Kotlin DSL gives:
- **Compile-time correctness** — missing fields are compiler errors, not runtime surprises
- **IDE autocomplete and refactoring support**
- **Readable, self-documenting product specifications**
- **Unit-testable product configurations** — `LendingProductDslTest` verifies every field

### Why the Outbox Pattern?

Writing loan state changes and domain events in the same database transaction (via the outbox table) guarantees atomicity without a distributed transaction coordinator. If the application crashes after committing but before sending to a message broker, the outbox processor will retry on restart.

### Why `Money` as a Value Object?

`Money` encapsulates both amount and currency, preventing operations on mismatched currencies at runtime. All arithmetic operators (`+`, `-`, `*`) enforce currency consistency and apply proper rounding (`HALF_UP`, 2 decimal places).

### Why `AllocationBucket` and `AllocationStrategy` in `domain.model`?

These enums represent fundamental domain concepts (what a balance is, how to split it) that are reused across the core layer (`PaymentCore`) and the product layer (`PaymentSlotConfig`). Placing them in `domain.model` prevents a circular dependency between `domain.core` and `domain.product`.
