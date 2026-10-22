# Changelog

All notable changes to this repository are documented here.

The format is based on Keep a Changelog and the project uses Semantic Versioning.

## [Unreleased]

### Added

- Repository baseline.
- Policy domain slice: tenant-scoped `/api/v1/policies` API, RestClient expert adapter, SPEC lifecycle enforcement, structured errors, and JUnit tests.
- Canonical RFC 9457 `application/problem+json` errors with `code`, `category`,
  `retryable`, `errors[{field, code, message}]`, `meta`, and correlation ids.
- Page envelope `{items, page, size, total, _links}` for `GET /api/v1/policies`
  with page/size validation and boundary links.
- `ETag` on detail and mutation responses plus `If-Match` optimistic concurrency
  on `PATCH /{id}/status`.
- Idempotent create via `Idempotency-Key`, including `Idempotent-Replayed`
  response headers.
- Second slice: public proxies for policy versions, coverage/limits with
  aggregate erosion, endorsements, insureds/locations, cancellation, and
  reinstatement under `/api/v1/policies/{policyId}`.
- Page envelopes with public `_links` for every second-slice collection.
- Second-slice mutation responses echo the expert `ETag` and
  `Idempotent-Replayed` header; `If-Match` and `Idempotency-Key` are validated
  before the expert call where the command supports them.
- Expert `422 BUSINESS_RULE` and `428 APPROVAL_REQUIRED` problems pass through
  unchanged.
- `GET /api/v1/policies/search` with the shared global-search envelope
  `{query, limit, items}`, case-insensitive contains matching, tenant scoping,
  a 10-default / 25-capped limit, and a `/policy?record=<id>` href. The endpoint
  requires `X-EIA-Book: DIRECT` and returns `404 BOOK_SCOPE_VIOLATION` for any
  other book.
- Optional `insuredPartyId` UUID filter on `GET /api/v1/policies`. The filtered
  read is tenant-scoped, preserves the `updated_at DESC, id ASC` order and page
  envelope, carries the filter into `_links`, and returns `400 INVALID_PARAMETER`
  for a malformed UUID.
- Billing plan proxy under `/api/v1/policies/{policyId}/billing`: `GET` returns
  the `{planId, planNumber, policyId, totalPremium, currency, installmentCount,
  frequency, firstDueDate, status, installments[]}` projection, `POST` creates a
  plan from `{installmentCount, frequency, firstDueDate}`, and `PATCH
  /billing/installments/{installmentId}` records `{status, paymentId?}` and
  returns the updated installment. The tenant and `X-EIA-Book: DIRECT` are
  forwarded to the expert, and expert `400`/`404`/`409` problems are preserved.
- `POST /api/v1/policies/{policyId}/renew` renews a policy into a successor
  term from `{effectiveDate, expirationDate, premiumAmount?, premiumCurrency?,
  limitAmount?, limitCurrency?}`. The successor `PolicyDto` exposes
  `renewedFromPolicyId`, the tenant and `X-EIA-Book: DIRECT` are forwarded, and
  expert `400`/`404`/`409` problems are preserved.
- `GET /api/v1/billing/installments?status=&dueBefore=&limit=` lists the
  tenant's premium receivables across policies. Each item is
  `{installmentId, planId, planNumber, policyId, policyNumber, insuredPartyId,
  installmentNumber, dueDate, amount, currency, status, daysPastDue, paidAt,
  paymentId, lastDunningLevel, lastDunningAt}` and the response is `{items,
  totalAmount, overdueAmount, count}`. The tenant and `X-EIA-Book: DIRECT` are
  forwarded, the `limit` defaults to 20 and is bounded to 1..200, and expert
  `400`/`404` problems are preserved.
- `POST`/`GET /api/v1/billing/installments/{installmentId}/dunning` records a
  dunning action from `{level, channel, notes?}` and lists the installment's
  dunning history newest first, both as the flat `{dunningId, installmentId,
  planId, policyId, level, channel, notes, status, sentAt}` projection. The
  tenant and `X-EIA-Book: DIRECT` are forwarded, non-DIRECT books are
  `404 BOOK_SCOPE_VIOLATION`, and expert `400`/`404`/`409` problems are
  preserved.
- `GET`/`POST /api/v1/policies/{policyId}/credit-notes` and
  `PATCH /api/v1/policies/{policyId}/credit-notes/{creditNoteId}/status`
  request, list, and advance the policy's credit notes as the flat
  `{creditNoteId, noteNumber, policyId, amount, currency, reason, status,
  issuedAt, appliedAt, createdAt}` projection with the amount as a decimal
  string. New notes start `DRAFT`; `ISSUED`/`APPLIED`/`CANCELLED` advance the
  expert-owned lifecycle. The tenant and `X-EIA-Book: DIRECT` are forwarded,
  non-DIRECT books are `404 BOOK_SCOPE_VIOLATION`, and expert
  `400`/`404`/`409` problems are preserved.
- `GET`/`PUT /api/v1/policies/{policyId}/mass-tort-profile` reads and replaces
  the mass tort profile `{profileId, policyId, tortType, triggerType,
  allocationMethod, allocationNotes, coverageBlockStart, coverageBlockEnd,
  status, updatedAt}`. The tenant and `X-EIA-Book: DIRECT` are forwarded,
  non-DIRECT books are `404 BOOK_SCOPE_VIOLATION`, and expert
  `400`/`404`/`409` problems are preserved.
- Program catalog and membership: `POST`/`GET /api/v1/programs`,
  `GET /api/v1/programs/{programId}`, and
  `PATCH /api/v1/policies/{policyId}/program` (null or absent clears the
  assignment). Programs project as `{programId, programNumber, name,
  programType, effectiveFrom, effectiveTo, status, leadPolicyId, capacityAmount,
  capacityCurrency, usedAmount, utilizationPct, policyCount, createdAt}` with
  the program number, status, and capacity aggregates generated by the expert;
  the list returns an array ordered newest first. `CreateProgramCommand` accepts
  the optional `capacityAmount`/`capacityCurrency` pair, validates that a
  supplied pair is complete with a positive amount and an upper-case ISO 4217
  currency, and forwards it; `capacityAmount`, `usedAmount`, and
  `utilizationPct` serialize as decimal strings. `PolicyDto` gains `programId`,
  the tenant and `X-EIA-Book: DIRECT` are forwarded, non-DIRECT books are
  `404 BOOK_SCOPE_VIOLATION`, and expert `400`/`404`/`409` problems are
  preserved.
- Legal entity catalog and policy issuer membership:
  `POST`/`GET /api/v1/legal-entities`,
  `GET /api/v1/legal-entities/{legalEntityId}`, and
  `PATCH /api/v1/policies/{policyId}/legal-entity`. Legal entities project as
  `{legalEntityId, code, name, entityType, jurisdiction, regulator,
  licenseNumber, status, createdAt}` with the identity, status, and creation
  instant generated by the expert; the list returns an array. `PolicyDto` gains
  `legalEntityId`, the tenant and `X-EIA-Book: DIRECT` are forwarded,
  non-DIRECT books are `404 BOOK_SCOPE_VIOLATION`, and expert
  `400`/`404`/`409` problems are preserved.
- Credit-note application to premium installments. The status command accepts
  an optional `installmentId` and forwards it only for `APPLIED`; the expert
  requires an open same-policy installment and credits the note amount against
  its uncredited remainder. Billing installments gain `creditedAmount` and
  `outstandingAmount`, the billing plan gains the `{totalAmount, paidAmount,
  waivedAmount, creditedAmount, outstandingAmount, overdueAmount, currency,
  installmentCount}` summary, and the credit-note projection gains
  `appliedInstallmentId`. Receivables gain `creditedAmount` and
  `outstandingAmount`, and their totals use the outstanding amounts so credits
  reduce the collectible queue.
- Credit-note reversal. The status command accepts an optional `reason` and
  forwards it only for `REVERSED`, while `installmentId` stays `APPLIED`-only.
  The credit-note projection gains `reversedAt` and `reversalReason`, and a
  reversed note keeps `issuedAt`, `appliedAt`, and `appliedInstallmentId` so
  the original application stays auditable. `REVERSED` is reachable from
  `APPLIED`, requires a non-blank reason capped at 500 characters, and releases
  the credit from the applied installment. Billing and receivables keep their
  existing shape while `creditedAmount`, `outstandingAmount`, the installment
  status, and the summary totals return to their pre-credit values; expert
  `400`/`404`/`409` problems are preserved.

### Changed

- `GET /api/v1/policies` now returns the page envelope instead of a bare array.
- Illegal lifecycle transitions and duplicate policy numbers are `409` conflicts
  (the business codes `INVALID_STATUS_TRANSITION` and `DUPLICATE_POLICY_NUMBER`
  are unchanged).
- Expert failures preserve their status, code, field errors, and metadata.

### Removed

- The legacy `{timestamp, status, code, message, path, details}` body.
