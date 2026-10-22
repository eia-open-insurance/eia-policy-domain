# Implementation notes

## Status

The Policy domain proxies both Policy slices. The first slice owns tenant
validation, create/status orchestration, and the public problem envelope. The
second slice adds pass-through routes for versions, coverage and limits,
aggregate erosion, endorsements, insureds and locations, cancellation,
reinstatement, and renewal into a successor term. Billing adds the plan and
installment schedule projection under the same tenant and DIRECT-book context,
and appends a positive premium adjustment installment when the policy premium
overtakes the ACTIVE plan total.
The premium-audit route applies an audited premium and returns the updated
policy plus the audit row.
The underwriting-submission slice proxies intake, status transitions, the
structured referral and its decision, the append-only referral history, and
conversion of one submission into a policy, returning the updated or converted
submission, the historical referral rounds, and, for conversion, the created
policy.
A cross-policy premium-receivable read projects installments as receivables for
the collections/ageing view, the installment dunning route records and reads the
collections actions, the credit-note route requests, lists, advances, and reverses
the billing credits raised by cancellations, endorsements, and audits, the mass
tort profile route reads and replaces the allocation basis that Direct Claims
uses, the legal entity catalog exposes the carriers and service companies
that issue or support the DIRECT book, the program catalog exposes the
commercial portfolios with their optional capacity limit and derived capacity
utilization, and the product catalog exposes the sellable taxonomy that
policies and underwriting submissions reference by product code.

## Structure

| Class | Responsibility |
|---|---|
| `PolicyController` / `PolicyService` | First-slice public routes and lifecycle |
| `PolicyContractController` / `PolicyContractService` | Second-slice public routes, renewal, page link rebuild, header validation |
| `PolicyBillingController` / `PolicyBillingService` | Billing plan, installment, and positive adjustment routes; DIRECT-book guard |
| `PolicyPremiumAuditController` / `PolicyPremiumAuditService` | Policy `POST .../premium-audit`; DIRECT-book guard, `If-Match` and idempotency validation |
| `PolicyMassTortProfileController` / `PolicyMassTortProfileService` | Mass tort profile read/replace route; DIRECT-book guard |
| `ReceivableController` / `ReceivableService` | Cross-policy `GET /api/v1/billing/installments`; status/limit normalization |
| `DunningController` / `DunningService` | Installment `GET`/`POST .../dunning`; DIRECT-book guard |
| `PolicyCreditNoteController` / `PolicyCreditNoteService` | Policy `GET`/`POST .../credit-notes` and `PATCH .../status`; DIRECT-book guard |
| `LegalEntityController` / `LegalEntityService` | Legal entity `POST`/`GET` catalog routes; DIRECT-book guard |
| `ProductController` / `ProductService` | Product `GET`/`POST` catalog routes plus the status `PATCH`; DIRECT-book guard |
| `ProductDto` | Public product projection with the ACTIVE/RETIRED status and aggregate version |
| `CreateProductCommand` | Product create body; shape validation and short aliases |
| `UpdateProductStatusCommand` | Product status body; shape validation only |
| `PolicyLegalEntityController` / `PolicyLegalEntityService` | Policy issuer `PATCH` route; DIRECT-book guard |
| `ProgramController` / `ProgramService` | Program `POST`/`GET` catalog routes; date and capacity-pair validation; DIRECT-book guard |
| `SubmissionController` / `SubmissionService` | Underwriting submission list/read/create/status/referral/decision/history/convert routes; DIRECT-book guard and idempotency normalization |
| `SubmissionDto` | Public underwriting submission projection with the canonical applicant, product, requested-limit, tort-code, notes, and timestamp members plus the optional converted-policy link and structured referral fields |
| `ReferralDto` | Flat public projection of one referral review round from the submission's append-only history |
| `CreateSubmissionCommand` | Submission create body; applicant/product/requested-term/limit requirements and optional tort codes and notes |
| `UpdateSubmissionStatusCommand` | Submission status body; shape validation only |
| `ReferSubmissionCommand` | Structured-referral body; required reason, authority, and assignee plus optional notes |
| `DecideReferralCommand` | Referral-decision body; required decision and decider plus optional notes |
| `ConvertSubmissionCommand` | Submission conversion body; required premium and currency plus the optional expiration override |
| `SubmissionConversionResult` | Public `{submission, policy}` conversion envelope |
| `ProgramDto` | Public program projection with the optional capacity limit and expert-derived aggregates |
| `CreateProgramCommand` | Program create body; shape validation and the optional capacity pair |
| `BillingPlan`, `BillingSummary`, `Installment` | Public billing projections and their credited/outstanding totals |
| `BillingAdjustmentCommand` | Positive premium adjustment body; shape validation only |
| `BillingAdjustmentResult` | Public `{plan, installment, adjustmentAmount, newTotalPremium}` envelope |
| `PremiumAuditResult` | Public envelope used when the expert returns the updated policy and an audit row |
| `PremiumAuditDto` | Audit-row projection used by that envelope |
| `ApplyPremiumAuditCommand` | Premium-audit request body; shape validation only |
| `LegalEntityDto` | Public legal entity projection |
| `CreateLegalEntityCommand` | Legal entity create body; shape validation only |
| `UpdatePolicyLegalEntityCommand` | Policy issuer body; shape validation only |
| `DunningAction` | Public installment dunning projection |
| `CreateDunningActionCommand` | Dunning action body; shape validation only |
| `CreditNote` | Public credit-note projection |
| `CreateCreditNoteCommand` | Credit-note request body; shape validation only |
| `UpdateCreditNoteStatusCommand` | Credit-note status body; optional APPLIED installment and REVERSED reason |
| `MassTortProfile` | Public mass tort profile projection |
| `UpdateMassTortProfileCommand` | Mass tort profile replace body; shape validation only |
| `Receivable`, `ReceivableSummary` | Public receivable projection and its totals |
| `RenewPolicyCommand` | Renewal request body; optional premium and limit overrides |
| `PolicyClient` | Anti-corruption boundary to the expert; translates expert problems |
| `PolicyQueryRepository` | Tenant-scoped direct read for the `insuredPartyId` list filter |
| `PolicyLifecycle` | Status transition table |
| `ItemPage`, `Mutation`, `EntityTags`, `Pagination`, `PageLinks` | Shared response and concurrency helpers |

## Billing expert contract

The domain forwards `GET` and `POST /internal/v1/policies/{policyId}/billing`
and `PATCH /internal/v1/policies/{policyId}/billing/installments/{installmentId}`
with `X-EIA-Tenant`, `X-EIA-Book: DIRECT`, and the `tenantId` query scope. The
expert returns `{plan, installments, summary}`. Each installment carries the
gross `amount`, the `creditedAmount` accumulated from applied credit notes, and
the derived `outstandingAmount`; the summary is `{totalAmount, paidAmount,
waivedAmount, creditedAmount, outstandingAmount, overdueAmount, currency,
installmentCount}` and nets the credited premium out of the collectible
balance. The domain flattens the plan into the public projection and always
publishes a summary, merging the expert values and deriving what an older
expert omits from the plan total and installment rows. The expert owns the
schedule, the split, the credit application, and the payment/waiver rules; this
repository contains the domain adapter only.

`POST /internal/v1/policies/{policyId}/billing/adjustment` forwards
`{dueDate, reason}` together with `X-EIA-Tenant`, `X-EIA-Book: DIRECT`, the
optional `If-Match`, and the optional `Idempotency-Key` and returns
`{plan, installment, adjustmentAmount, newTotalPremium}`. The expert derives the
adjustment amount as the policy written premium less the ACTIVE plan total,
appends one DUE installment, and moves the plan total/count by the same amount;
the public envelope projects the plan into the flat `BillingPlan` shape without
repeating the schedule and the appended row into `Installment`. A fresh command
is `201`; an expert replay is `200` with `Idempotent-Replayed: true`. A
non-positive remainder is a preserved `400 NO_PREMIUM_ADJUSTMENT`, a missing
plan is `404 BILLING_PLAN_NOT_FOUND`, and a non-ACTIVE or full schedule is a
preserved `409 BILLING_PLAN_NOT_ACTIVE` / `409 MAX_INSTALLMENTS_REACHED`. The
expert side of this contract lives in `eia-policy-expert`; this repository
contains the domain adapter only.

## Premium-receivable expert contract

The domain forwards `GET /internal/v1/billing/installments` with
`X-EIA-Tenant` and `X-EIA-Book: DIRECT`, the `tenantId` query scope, and the
optional `status` (`DUE` or `OVERDUE`) and inclusive `dueBefore` filters plus
the normalized `limit` (1..200, default 20). The expert returns
`{items, totalAmount, overdueAmount, count}` with items ordered by due date,
policy number, and installment number, each item carrying the plan, policy, and
insured-party context, a `daysPastDue` age, the gross `amount`, the
`creditedAmount`, and the `outstandingAmount`. `amount` stays gross;
`outstandingAmount` is `amount - creditedAmount` for the open remainder, and
`totalAmount`/`overdueAmount` sum the outstanding amounts so credits reduce the
collectible queue. The domain recomputes totals, count, and `daysPastDue` only
when the expert omits them, using outstanding amounts, so the public summary
stays coherent during rollout. The expert owns that projection; this repository
contains the domain side of the contract only.

## Installment dunning expert contract

The domain forwards `GET` and `POST /internal/v1/billing/installments/{installmentId}/dunning`
with `X-EIA-Tenant`, `X-EIA-Book: DIRECT`, and the `tenantId` query scope. `POST`
sends `{level, channel, notes?}` and both methods return the flat action
`{dunningId, installmentId, planId, policyId, level, channel, notes, status,
sentAt}`; the domain accepts the expert's `id` alias for `dunningId`. `GET`
returns the installment's dunning history newest first, or an empty array when
none exists. The expert owns the level/channel vocabulary, the DUE/OVERDUE
collectible-state rule, and the one-action-per-level constraint, so a settled
installment is `409 INSTALLMENT_NOT_DUNNABLE` and a repeated level is
`409 DUNNING_ALREADY_SENT`. A missing or cross-tenant installment is
`404 INSTALLMENT_NOT_FOUND`, and every `400` is a preserved expert problem.
The expert side of this contract lives in `eia-policy-expert`; this repository
contains the domain adapter only.

## Policy credit-note expert contract

The domain forwards `GET` and `POST /internal/v1/policies/{policyId}/credit-notes`
and `PATCH /internal/v1/policies/{policyId}/credit-notes/{creditNoteId}/status`
with `X-EIA-Tenant`, `X-EIA-Book: DIRECT`, and the `tenantId` query scope.
`POST` sends `{amount, currency, reason?, cancellationId?}`; `PATCH` sends
`{status, installmentId?, reason?}` and includes `installmentId` only when the
target status is `APPLIED` and `reason` only when it is `REVERSED`, so ISSUED
and CANCELLED keep the unchanged `{status}` payload. The expert returns the flat
note `{creditNoteId, noteNumber, policyId, amount, currency, reason, status,
issuedAt, appliedAt, appliedInstallmentId, createdAt, reversedAt,
reversalReason, cancellationId, cancellationNumber}`; the domain accepts the
expert's `id` identity and SPEC column aliases (`note_number`, `policy_id`,
`issued_at`, `applied_at`, `applied_installment_id`, `created_at`,
`reversed_at`, `reversal_reason`, `cancellation_id`, `cancellation_number`).
`GET` returns the policy's notes newest first, or an empty array when none
exists. The optional `cancellationId` links the note to the cancellation whose
return premium it credits; the expert validates its tenant/policy scope and
refund eligibility, and a linked note carries the cancellation id plus its
human-readable `cancellationNumber` while every other note leaves both absent.
The expert owns the note
number, the `DRAFT`/`ISSUED`/`APPLIED`/`CANCELLED`/`REVERSED` lifecycle, the
policy-currency match, the optional 500-character creation reason, the APPLIED
open-installment and amount-fit rules, the approval gate, and the SAP
credit-memo reference. `REVERSED` is reachable only from `APPLIED`, requires a
non-blank `reason`, stamps `reversedAt` and `reversalReason`, keeps the
application timestamps and installment for audit, and releases the note amount
from the credited installment, reopening a fully CREDITED row as DUE/OVERDUE.
The billing and receivable projections keep their shape; their
`creditedAmount` and `outstandingAmount` values simply return to the
pre-credit state after a reversal. An unknown currency is a preserved `400
CURRENCY_MISMATCH`, a blank or over-long reversal reason is `400
INVALID_CREDIT_NOTE_REASON`, a missing policy or note is `404 POLICY_NOT_FOUND`
/ `404 CREDIT_NOTE_NOT_FOUND`, a missing/foreign installment is
`404 INSTALLMENT_NOT_FOUND`, an ineligible installment is `409
INSTALLMENT_NOT_CREDITABLE`, an amount above the uncredited remainder is `409
CREDIT_NOTE_EXCEEDS_INSTALLMENT`, and an out-of-order or terminal transition is
`409 INVALID_CREDIT_NOTE_TRANSITION`. The expert side of this contract lives in
`eia-policy-expert`; this repository contains the domain adapter only.

## Premium-audit expert contract

The domain forwards `POST /internal/v1/policies/{policyId}/premium-audit` with
`X-EIA-Tenant`, `X-EIA-Book: DIRECT`, the `tenantId` query scope, and the
optional `If-Match` and `Idempotency-Key` headers. The payload is
`{auditedPremium, effectiveDate, reason, approvedBy?, approvalRef?}`, with the
two approval members omitted when the caller does not supply them. The current
expert returns the updated `PolicyDto` directly with `201` and the advanced
`ETag`; the domain publishes that policy DTO as the response body. It also
accepts an envelope carrying the updated policy and the audit row: the policy
may sit under `policy`, `updatedPolicy`, or `policyDto` and the audit under
`premiumAudit`, `audit`, `auditResult`, or `result`; in that case the domain
publishes `{policy, premiumAudit}`. The audit projection is
`{auditId, policyId, auditedPremium, previousPremium, premiumDelta, currency,
effectiveDate, reason, approvedBy, approvalRef, status, createdAt}` with the
monetary members serialized as decimal strings; the domain accepts the expert's
`id`/`premiumAuditId` identity and SPEC-style aliases (`policy_id`,
`audited_premium`, `previous_premium`, `premium_delta`, `audited_premium_amount`,
`previous_premium_amount`, `premium_delta_amount`, `premium_currency`,
`effective_date`, `audit_date`, `approved_by`, `approval_ref`, `created_at`,
`auditedAt`). A fresh application returns `201`; a response carrying
`Idempotent-Replayed: true` or a `200` status is published as `200`. The expert
owns the audit eligibility, the new policy version, the previous-premium/delta
calculation, the approval gate, and the posting event, so its `400`/`404`/`409`
problems are preserved. A missing policy is `404 POLICY_NOT_FOUND`, a stale
`If-Match` is `409 CONCURRENT_MODIFICATION`, a no-op audit is
`400 NO_PREMIUM_CHANGE`, and an ineligible status is
`409 PREMIUM_AUDIT_NOT_ALLOWED`. The current expert does not replay on
`Idempotency-Key`; the domain forwards the key so a future expert can implement
that replay contract without a domain change. The expert side of this contract
lives in `eia-policy-expert`; this repository contains the domain adapter only.

## Mass tort profile expert contract

The domain forwards `GET` and `PUT /internal/v1/policies/{policyId}/mass-tort-profile`
with `X-EIA-Tenant`, `X-EIA-Book: DIRECT`, and the `tenantId` query scope. The
expert returns the one-per-policy profile `{id, tenantId, policyId, tortType,
triggerType, allocationMethod, allocationNotes, coverageBlockStart,
coverageBlockEnd, status, updatedAt}`; the domain renames `id` to `profileId` and
publishes the flat projection without `tenantId`. `PUT` is a replace that the
expert implements as an upsert; the domain returns the stored projection. The
expert owns the `DRAFT`/`COMPLETE` status vocabulary, the tort/trigger/allocation
enumerations, the 2000-character notes cap, the inclusive coverage-block date
order, and the COMPLETE gate, so every `400` is a preserved expert problem. A
missing policy is `404 POLICY_NOT_FOUND`, a policy without a profile is
`404 MASS_TORT_PROFILE_NOT_FOUND`, and a concurrent replace is
`409 CONCURRENT_MODIFICATION`. The expert side of this contract lives in
`eia-policy-expert`; this repository contains the domain adapter only.

## Product catalog expert contract

The domain forwards `GET` and `POST /internal/v1/products`,
`GET /internal/v1/products/{productId}`, and
`PATCH /internal/v1/products/{productId}/status` with `X-EIA-Tenant`,
`X-EIA-Book: DIRECT`, and the `tenantId` query scope. `GET` forwards the
optional `q` (case-insensitive code/name substring) and `status` (ACTIVE or
RETIRED) filters and returns the tenant's products as an array ordered by
product code, or an empty array. `POST` sends `{productCode, productName,
productLine, jurisdiction}` and the expert creates an ACTIVE product; the
public body also accepts the `code`, `name`, and `line` aliases. `PATCH` sends
`{status}` and retires or reactivates the product.

The expert returns the flat product `{productId, productCode, productName,
productLine, jurisdiction, status, version, createdAt, updatedAt}`; the domain
accepts the expert's `id` identity and the SPEC column aliases (`product_id`,
`product_code`, `product_name`, `product_line`, `created_at`, `updated_at`) as
well as a bare row, a `{product}` envelope, a bare array, or an `{items}` page.
`version` is the aggregate sequence and backs the strong `ETag` on reads,
creates, and status changes; the domain falls back to `version` when an older
expert omits the header. The expert owns the product-line and status
vocabularies, the two-letter ISO jurisdiction, the upper-case code/line
normalization, the code and name caps, tenant-scoped code uniqueness, and the
ACTIVE/RETIRED lifecycle, so its `400`/`404`/`409` problems are preserved: a
missing product is `404 PRODUCT_NOT_FOUND`, a duplicate code is
`409 DUPLICATE_PRODUCT_CODE`, and a concurrent status change is
`409 CONCURRENT_MODIFICATION`. The expert side of this contract lives in
`eia-policy-expert`; this repository contains the domain adapter only.

## Program capacity expert contract

The domain forwards `GET` and `POST /internal/v1/programs` and
`GET /internal/v1/programs/{programId}` with `X-EIA-Tenant`,
`X-EIA-Book: DIRECT`, and the `tenantId` query scope. `POST` sends
`{name, programType, effectiveFrom, effectiveTo, leadPolicyId?,
capacityAmount?, capacityCurrency?}`. The expert returns the flat program
`{programId, programNumber, name, programType, effectiveFrom, effectiveTo,
status, leadPolicyId, capacityAmount, capacityCurrency, usedAmount,
utilizationPct, policyCount, createdAt}`; the domain accepts the expert's `id`
identity and the SPEC/column aliases (`program_code`, `program_name`,
`program_type`, `effective_from`, `effective_to`, `program_status`,
`lead_policy_id`, `capacity_amount`, `capacity_currency`, `used_amount`,
`utilization_pct`, `policy_count`, `created_at`). The capacity limit is an
optional amount/currency pair; the domain rejects a supplied pair with one
member missing as `400 VALIDATION_ERROR` (`CAPACITY_AMOUNT_REQUIRED` or
`CAPACITY_CURRENCY_REQUIRED`), a non-positive amount as `MIN_VALUE`, and a
malformed currency as `INVALID_FORMAT`. `usedAmount`, `utilizationPct`, and
`policyCount` are expert-derived over the policies assigned to the program; the
domain publishes them unchanged and tolerates an expert that omits them. The
expert owns the program number, the type/status vocabulary, tenant-scoped
uniqueness, the effective-date rules, the membership and lead-policy rules, and
the capacity aggregates, so every create/assignment `400`, `404`, or `409`
problem is preserved. The expert side of this contract lives in
`eia-policy-expert`; this repository contains the domain adapter only.

## Legal entity expert contract

The domain forwards `GET` and `POST /internal/v1/legal-entities` and
`GET /internal/v1/legal-entities/{legalEntityId}` with `X-EIA-Tenant`,
`X-EIA-Book: DIRECT`, and the `tenantId` query scope. `POST` sends
`{code, name, entityType, jurisdiction, regulator?, licenseNumber?}`; the expert
returns the flat entity `{legalEntityId, code, name, entityType, jurisdiction,
regulator, licenseNumber, status, createdAt}`; the domain accepts the expert's
`id` identity and the SPEC column aliases (`entity_code`, `legal_name`,
`entity_type`, `domicile_country`, `license_number`, `created_at`). `GET`
returns the tenant's entities as an array, or an empty array when none exists.
`PATCH /internal/v1/policies/{policyId}/legal-entity` sends `{legalEntityId}`
and returns the updated `PolicyDto` with the expert `ETag`; the domain exposes
the new `legalEntityId` on every `PolicyDto` route and the per-tenant filtered
policy read maps `legal_entity_id` from the policy row. The expert owns the
entity code, the entity-type and status vocabularies, tenant-scoped code
uniqueness, the regulator and license rules, and the policy issuer and capacity
rules, so a bad entity type is a preserved `400`, a missing or cross-tenant
entity is `404 LEGAL_ENTITY_NOT_FOUND`, a duplicate code is
`409 DUPLICATE_LEGAL_ENTITY_CODE`, and a stale assignment is
`409 CONCURRENT_MODIFICATION`. The expert side of this contract lives in
`eia-policy-expert`; this repository contains the domain adapter only.

## Underwriting submission expert contract

The domain forwards `GET`/`POST /internal/v1/policies/submissions`,
`GET /internal/v1/policies/submissions/{submissionId}`, and
`PATCH /internal/v1/policies/submissions/{submissionId}/status` with
`X-EIA-Tenant`, `X-EIA-Book: DIRECT`, and the `tenantId` query scope. `POST`
sends the expert canonical `{applicantPartyId, productCode,
requestedEffectiveDate, requestedLimitAmount, requestedLimitCurrency,
tortCodes[], notes?}`; `PATCH` sends `{status, reason?}` and includes `reason`
only when it is supplied.

The canonical public projection is `{submissionId, submissionNumber, tenantId,
applicantPartyId, productCode, requestedEffectiveDate, requestedLimitAmount,
requestedLimitCurrency, tortCodes[], notes, status, version, createdAt,
updatedAt, policyId?}`. The expert row carries the remaining members; the domain
fills `tenantId` from the scoped request because the current expert deliberately
does not leak the tenant column, and publishes the optional `policyId` only when
the expert exposes the converted-policy link. The domain accepts a bare row or a
`{submission, ...}` envelope on every single-submission read and mutation, and
`GET` on the collection accepts either a bare array or an `{items, ...}` page
envelope; the public list is always a bare array. The domain also accepts the
expert's `id` identity and the SPEC column aliases (`submission_id`,
`submission_number`, `tenant_id`, `applicant_party_id`, `product_code`,
`requested_effective_date`, `requested_limit_amount`,
`requested_limit_currency`, `tort_codes`, `policy_id`, `created_at`,
`updated_at`).

The optional `Idempotency-Key` is forwarded on create and status; an expert
replay is `200` with `Idempotent-Replayed: true`. The optional `If-Match` is
forwarded on status unchanged, so the expert validates any concurrency tag. The
expert owns the submission number, the applicant and product rules, the
`RECEIVED`/`REFERRED`/`QUOTED`/`BOUND`/`DECLINED`/`WITHDRAWN` lifecycle, and the
quote/bind workflow, so its `400`/`404`/`409` problems are preserved. The
expert side of this contract lives in `eia-policy-expert`; this repository
contains the domain adapter only.

## Submission referral expert contract

`POST /api/v1/policies/submissions/{submissionId}/referral` opens or replaces
the structured referral from `{reason, authority, referredTo, notes?}`, and
`POST /api/v1/policies/submissions/{submissionId}/referral/decision` decides it
from `{decision, decidedBy, notes?}`. The domain calls the matching
`/internal/v1/policies/submissions/{submissionId}/referral` and
`.../referral/decision` routes with `X-EIA-Tenant`, `X-EIA-Book: DIRECT`, the
`tenantId` query scope, and the optional `If-Match` and `Idempotency-Key`
headers. The expert owns the referrable state (`RECEIVED` or `REFERRED`), the
`APPROVED`/`DECLINED` decision vocabulary, the pending rule, and the resulting
lifecycle movement: opening a referral moves the submission to `REFERRED` with
`referralDecision=PENDING`, `APPROVED` keeps it `REFERRED`, and `DECLINED` also
moves it to `DECLINED`. Both commands return `200` with the updated submission
projection and its version as the `ETag`; the domain echoes that tag (falling
back to `version`), publishes `Idempotent-Replayed: false` unless the expert
supplies an explicit replay marker, and preserves the expert `400`/`404`/`409`
problems, including `SUBMISSION_NOT_REFERRABLE` and `REFERRAL_NOT_PENDING`.
The projection carries the structured referral members
`{referralReason, referralAuthority, referredTo, referralNotes,
referralDecision, referralDecidedBy, referralDecidedAt}`; the domain accepts
the expert's snake-case column aliases during rollout and omits the members
until a referral is opened. The expert side of this contract lives in
`eia-policy-expert`; this repository contains the domain adapter only.

## Submission referral history expert contract

`GET /api/v1/policies/submissions/{submissionId}/referrals` returns the
submission's referral review rounds as an array, newest first. The domain calls
`GET /internal/v1/policies/submissions/{submissionId}/referrals` with
`X-EIA-Tenant`, `X-EIA-Book: DIRECT`, and the `tenantId` query scope, and
publishes a bare array even when an expert envelope uses `items` or `referrals`.
The expert owns the append-only history: every accepted refer command inserts a
round with `decision=PENDING`, and the decision command closes the newest
pending round with the decider and instant, so a review that went through
several rounds is reconstructable. The expert also owns the newest-first order
(`created_at DESC, id DESC`) and resolves the parent submission first, so an
unknown or another tenant's submission is `404 SUBMISSION_NOT_FOUND` even when
it has no history rows.

The public `ReferralDto` is the flat
`{referralId, submissionId, reason, authority, referredTo, notes, decision,
decidedBy, decidedAt, createdAt}` projection. `notes` is the referral narrative
when the round is opened; a non-blank decision note replaces it, matching the
submission projection's `referral_notes` column. `decidedBy` and `decidedAt`
are null while the round is `PENDING`. The domain accepts the expert's canonical
camel-case view and the snake-case aliases (`id`/`referral_id`,
`submission_id`, `referral_reason`, `required_authority`, `assigned_to`,
`comments`, `referral_decision`, `decided_by`, `decided_at`, `created_at`)
during rollout, fills `submissionId` from the scoped path when a row omits it,
and treats a missing row identity or a malformed collection as `502
EXPERT_BAD_RESPONSE`. The expert side of this contract lives in
`eia-policy-expert`; this repository contains the domain adapter only.

## Submission conversion expert contract

`POST /api/v1/policies/submissions/{submissionId}/convert` converts one
submission into a policy. The body is `{premiumAmount, premiumCurrency,
expirationDate?}`; premium and currency are required, and the expiration
override is forwarded only when supplied. The domain calls
`POST /internal/v1/policies/submissions/{submissionId}/convert` with
`X-EIA-Tenant`, `X-EIA-Book: DIRECT`, the `tenantId` query scope, and the
normalized optional `Idempotency-Key`.

The expert owns the conversion eligibility, the submission-to-policy linkage,
and both projections. The public response is the expert `{submission, policy}`
envelope: `submission` is the canonical projection above (carrying `policyId`
when the expert exposes the converted-policy link, and omitting the member when
it does not) and `policy` is the same `PolicyDto` published by the policy
routes. An expert that returns only the created policy is also accepted: the
domain reads the converted submission back with the same tenant scope and
composes the public envelope. A fresh conversion is `201`; an expert replay is
`200` with `Idempotent-Replayed: true`. The expert's `400`/`404`/`409` problems
are preserved unchanged, covering a non-convertible submission status, an
unknown or cross-tenant submission, an already-converted submission, and an
idempotency-key conflict. The expert side of this contract lives in
`eia-policy-expert`; this repository contains the domain adapter only.

## Verification

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-22.0.2.9-hotspot'
.\gradlew.bat build
```

`PolicyContractControllerTest` covers every new route, `PolicyContractServiceTest`
covers page-link rebuilding and header validation, and `PolicyClientTest` covers
header forwarding and expert problem translation. `PolicyBillingControllerTest`
covers the billing plan, installment, and positive adjustment routes,
validation, tenant/book handling, and problem propagation;
`PolicyBillingClientTest` uses `MockRestServiceServer` for the
forwarded tenant/book headers, payloads, the installment list and its
credited/outstanding amounts, the summary mapping and derivation, and
preserved `400`/`404`/`409` problems. `PolicyBillingAdjustmentClientTest` uses
`MockRestServiceServer` for the adjustment payload, tenant/DIRECT context, the
optional `If-Match` and `Idempotency-Key`, the envelope mapping, the derived
new total, the replay marker, and preserved `400`/`404`/`409` problems;
`PolicyBillingAdjustmentServiceTest` covers header validation, idempotency
normalization, and client delegation. `PolicyRenewalClientTest` uses
`MockRestServiceServer` for the renewal tenant/book headers, new-term payload,
successor policy and `renewedFromPolicyId` response, and preserved
`400`/`404`/`409` problems. `PolicyReceivableClientTest` uses
`MockRestServiceServer` for the receivable tenant/DIRECT headers,
status/dueBefore/limit query, projection with credited/outstanding amounts, net
summary mapping and fallback, ageing fallback, and preserved `400`/`404`
problems; `PolicyReceivableControllerTest` covers the
route, tenant/book handling, parameter parsing, and problem propagation, and
`ReceivableServiceTest` covers status and limit normalization.
`PolicyMassTortProfileClientTest` uses `MockRestServiceServer` for the profile
tenant/DIRECT headers, the `id`/SPEC-column aliases, the replace payload, and
preserved `400`/`404`/`409` problems; `PolicyMassTortProfileControllerTest`
covers the read/replace routes, shape validation, tenant/book handling, and
problem propagation. `PolicyDunningClientTest` uses `MockRestServiceServer` for
the dunning tenant/DIRECT headers, the action payload, the `id` alias, and
preserved `400`/`404`/`409` problems; `DunningControllerTest` covers the
read/record routes, shape validation, tenant/book handling, and problem
propagation, and `DunningServiceTest` covers the delegation.
`PolicyCreditNoteClientTest` uses `MockRestServiceServer` for the credit-note
tenant/DIRECT headers, the request and status payloads including the APPLIED
`{status, installmentId}` body, the REVERSED `{status, reason}` body (and the
absence of the installment and of the reason for the transitions that ignore
them), the optional `cancellationId` create payload (and its absence when not
supplied), the identity/column aliases including
`reversed_at`/`reversal_reason` and
`cancellation_id`/`cancellation_number`, the applied installment, the
cancellation link, the reversal stamp and reason, the newest-first list,
and preserved `400`/`404`/`409` problems (including cancellation-link
problems); `PolicyCreditNoteControllerTest`
covers the list/request/status routes including the APPLIED binding, the
REVERSED reason, and the cancellation link, shape validation including the
reason cap, tenant/book handling, and problem propagation, and
`PolicyCreditNoteServiceTest` covers the delegation.
`PolicyBillingClientTest` and `PolicyReceivableClientTest` also
cover the post-reversal readings where the credited amount is released and
the outstanding amount is restored without a projection shape change.
`LegalEntityClientTest` uses `MockRestServiceServer` for the legal entity
tenant/DIRECT headers, the create payload, the `id`/SPEC-column aliases, the
issuer assignment, and preserved `400`/`404`/`409` problems;
`LegalEntityControllerTest` covers the create/list/read routes, shape
validation, tenant/book handling, and problem propagation, and
`PolicyLegalEntityControllerTest` covers the issuer assignment route, body
validation, tenant/book handling, ETag echo, and problem propagation.
`ProgramClientTest` uses `MockRestServiceServer` for the program tenant/DIRECT
headers, the create payload with and without the capacity pair, the
`id`/SPEC-column/capacity aliases, the capacity projection including an expert
that omits the derived members, and preserved `400`/`404`/`409` problems;
`ProgramControllerTest` covers the create/list/read routes, the capacity-pair
validation, tenant/book handling, and problem propagation.
`ProductClientTest` uses `MockRestServiceServer` for the product tenant/DIRECT
headers, the `q`/`status` query, the create and status payloads, the bare and
`{items}`/`{product}` expert shapes, the `id`/SPEC-column aliases, ETag
forwarding, and preserved `400`/`404`/`409` problems including the bare-404
`PRODUCT_NOT_FOUND` fallback; `ProductControllerTest` covers the list/search/
read/create/status routes, the short-body aliases, shape validation,
tenant/book handling, the ETag fallback, and problem propagation, and
`ProductServiceTest` covers filter normalization and delegation.
`SubmissionClientTest` uses `MockRestServiceServer` for the underwriting
submission tenant/DIRECT headers, the canonical projection and tenant fallback,
the bare-array and `{items}` list shapes, the bare and `{submission}` single-row
shapes, the canonical create payload with and without an idempotency key, the
status payload with and without a reason, `If-Match` forwarding, the ETag and
replay mapping, the SPEC-column aliases, and preserved `400`/`404`/`409`
problems; `SubmissionConversionClientTest` uses `MockRestServiceServer` for the
conversion tenant/DIRECT/idempotency headers, the canonical terms payload with
and without the optional expiration date, the `{submission, policy}` envelope,
the bare-policy expert shape with its follow-up submission read, the converted
`policyId` mapping (and its absence on an older expert), and preserved
`400`/`404`/`409` problems; `SubmissionReferralClientTest` uses
`MockRestServiceServer` for the referral and decision tenant/DIRECT/If-Match/
idempotency headers, the canonical `{reason, authority, referredTo, notes?}`
and `{decision, decidedBy, notes?}` payloads, the optional-member omission, the
updated-submission response, the explicit replay marker, the SPEC-column
aliases, and preserved `400`/`404`/`409` problems; `SubmissionReferralControllerTest`
covers both routes, the updated projection and ETag fallback, body validation
and caps, tenant/book handling, the absent replay marker, and problem
propagation, and `SubmissionReferralServiceTest` covers delegation and
idempotency normalization. `SubmissionReferralHistoryClientTest` uses
`MockRestServiceServer` for the history tenant/DIRECT headers, the bare-array
and `{items}`/`{referrals}` list shapes, the canonical and SPEC-column alias
mapping, the empty history, the `submissionId` fallback, the pending-round null
decision fields, the malformed/empty `EXPERT_BAD_RESPONSE` boundaries, and a
preserved `404`; `SubmissionReferralHistoryControllerTest` covers the array
response, the empty array, tenant/book handling, and problem propagation, and
`SubmissionReferralHistoryServiceTest` covers delegation, the empty history, and
the expert-not-found propagation. `SubmissionControllerTest` covers the
list/read/create/status/convert routes, the canonical projection, ETag
derivation and echo, `201`/`200` selection, the expert create caps, tenant/book
handling, and problem propagation, and `SubmissionServiceTest` covers delegation
and idempotency normalization.
`PolicyPremiumAuditClientTest` uses `MockRestServiceServer` for the
premium-audit tenant/DIRECT headers, the request payload with and without the
optional approval members, the bare policy response (the current expert shape),
the optional `{policy, premiumAudit}` envelope, the `audit` alias, the replay
marker, the ETag, and preserved `400`/`404`/`409` problems;
`PolicyPremiumAuditControllerTest` covers the route, `201`/`200` status
selection, the bare-policy and envelope response shapes, ETag echo and fallback,
body validation, tenant/book handling, and problem propagation, and
`PolicyPremiumAuditServiceTest` covers delegation, idempotency-key
normalization, and `If-Match`/key rejection before the expert call.

## Known SPEC gaps in this slice

- The domain performs no independent business-rule validation for the second
  slice or billing; it validates request shape, page bounds, ETag syntax, and
  idempotency keys, then relies on the expert for the SPEC invariants. Program
  capacity aggregates are likewise expert-derived; the domain forwards the
  optional limit and publishes the returned used amount, utilization, and
  policy count unchanged.
- There is no transaction catalog or asynchronous orchestration; every command
  is a synchronous expert call.
- Approvals are represented by `approvedBy` / `approvalRef` identifiers supplied
  by the caller; no approval workflow service is integrated.
- The domain does not proxy occurrences, claims, reinsurance, billing accounts,
  billing instructions, disputes, commission statements, producer payables, or
  lineage projections (SPEC 11.2.10/11.2.13) yet. Underwriting submissions are
  proxied as intake, status transitions, the structured referral, its decision,
  and its append-only history, plus conversion to a policy; clearance, rating
  worksheets, producer licensing, and authority evaluation are not modelled or
  orchestrated here.
