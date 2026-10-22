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
A cross-policy premium-receivable read projects installments as receivables for
the collections/ageing view, the installment dunning route records and reads the
collections actions, the credit-note route requests, lists, advances, and reverses
the billing credits raised by cancellations, endorsements, and audits, the mass
tort profile route reads and replaces the allocation basis that Direct Claims
uses, the legal entity catalog exposes the carriers and service companies
that issue or support the DIRECT book, and the program catalog exposes the
commercial portfolios with their optional capacity limit and derived capacity
utilization.

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
| `PolicyLegalEntityController` / `PolicyLegalEntityService` | Policy issuer `PATCH` route; DIRECT-book guard |
| `ProgramController` / `ProgramService` | Program `POST`/`GET` catalog routes; date and capacity-pair validation; DIRECT-book guard |
| `SubmissionController` / `SubmissionService` | Underwriting submission list/read/create/status routes; DIRECT-book guard and idempotency normalization |
| `SubmissionDto` | Public underwriting submission projection with JSON requested-limits and exposures documents |
| `CreateSubmissionCommand` | Submission create body; applicant/product requirements and optional requested-risk facts |
| `UpdateSubmissionStatusCommand` | Submission status body; shape validation only |
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
sends `{submissionNumber?, applicantPartyId, productId, programId?,
requestedEffectiveDate?, requestedLimits?, requestedCoverages?, tortCodes?,
exposures?, owner?}`; `PATCH` sends `{status, reason?}` and includes `reason`
only when it is supplied. The expert returns the flat `{submissionId,
submissionNumber, applicantPartyId, productId, programId,
requestedEffectiveDate, requestedLimits, requestedCoverages, tortCodes,
exposures, status, owner, receivedAt, updatedAt, version?}` row; the domain
accepts the expert's `id` identity and the SPEC column aliases (`submission_id`,
`submission_number`, `applicant_party_id`, `product_id`, `program_id`,
`requested_effective_date`, `requested_limits`, `requested_coverages`,
`tort_codes`, `received_at`, `updated_at`). `GET` on the collection accepts
either a bare array or a page envelope with `items` and publishes an array; the
`requestedLimits` and `exposures` documents are passed through as JSON. The
optional `Idempotency-Key` is forwarded on create and status; an expert replay
is `200` with `Idempotent-Replayed: true`. The optional `If-Match` is forwarded
on status unchanged, so the expert validates any concurrency tag. The expert
owns the submission number, the applicant and product rules, the
`RECEIVED`/`CLEARANCE`/`RATING`/`REFERRED`/`QUOTED`/`BOUND`/`DECLINED`/
`WITHDRAWN` lifecycle, clearance, referral, authority, quote, and bind, so its
`400`/`404`/`409` problems are preserved. The expert side of this contract lives
in `eia-policy-expert`; this repository contains the domain adapter only.

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
`SubmissionClientTest` uses `MockRestServiceServer` for the underwriting
submission tenant/DIRECT headers, the array and page-envelope list shapes, the
create payload with and without an idempotency key, the status payload with and
without a reason, `If-Match` forwarding, the ETag and replay mapping, and
preserved `400`/`404`/`409` problems; `SubmissionControllerTest` covers the
list/read/create/status routes, the array projection, ETag derivation and echo,
`201`/`200` selection, shape validation, tenant/book handling, and problem
propagation, and `SubmissionServiceTest` covers delegation and idempotency
normalization.
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
  proxied, but clearance, referral, rating worksheets, quote, bind, producer
  licensing, and authority evaluation remain expert-owned and are not
  orchestrated here.
