# Implementation notes

## Status

The Policy domain proxies both Policy slices. The first slice owns tenant
validation, create/status orchestration, and the public problem envelope. The
second slice adds pass-through routes for versions, coverage and limits,
aggregate erosion, endorsements, insureds and locations, cancellation,
reinstatement, and renewal into a successor term. Billing adds the plan and
installment schedule projection under the same tenant and DIRECT-book context.
A cross-policy premium-receivable read projects installments as receivables for
the collections/ageing view, the installment dunning route records and reads the
collections actions, the credit-note route requests, lists, and advances the
billing credits raised by cancellations, endorsements, and audits, and the mass
tort profile route reads and replaces the allocation basis that Direct Claims
uses.

## Structure

| Class | Responsibility |
|---|---|
| `PolicyController` / `PolicyService` | First-slice public routes and lifecycle |
| `PolicyContractController` / `PolicyContractService` | Second-slice public routes, renewal, page link rebuild, header validation |
| `PolicyBillingController` / `PolicyBillingService` | Billing plan and installment routes; DIRECT-book guard |
| `PolicyMassTortProfileController` / `PolicyMassTortProfileService` | Mass tort profile read/replace route; DIRECT-book guard |
| `ReceivableController` / `ReceivableService` | Cross-policy `GET /api/v1/billing/installments`; status/limit normalization |
| `DunningController` / `DunningService` | Installment `GET`/`POST .../dunning`; DIRECT-book guard |
| `PolicyCreditNoteController` / `PolicyCreditNoteService` | Policy `GET`/`POST .../credit-notes` and `PATCH .../status`; DIRECT-book guard |
| `BillingPlan`, `Installment` | Public billing projections |
| `DunningAction` | Public installment dunning projection |
| `CreateDunningActionCommand` | Dunning action body; shape validation only |
| `CreditNote` | Public credit-note projection |
| `CreateCreditNoteCommand` | Credit-note request body; shape validation only |
| `UpdateCreditNoteStatusCommand` | Credit-note status body; shape validation only |
| `MassTortProfile` | Public mass tort profile projection |
| `UpdateMassTortProfileCommand` | Mass tort profile replace body; shape validation only |
| `Receivable`, `ReceivableSummary` | Public receivable projection and its totals |
| `RenewPolicyCommand` | Renewal request body; optional premium and limit overrides |
| `PolicyClient` | Anti-corruption boundary to the expert; translates expert problems |
| `PolicyQueryRepository` | Tenant-scoped direct read for the `insuredPartyId` list filter |
| `PolicyLifecycle` | Status transition table |
| `ItemPage`, `Mutation`, `EntityTags`, `Pagination`, `PageLinks` | Shared response and concurrency helpers |

## Premium-receivable expert contract

The domain forwards `GET /internal/v1/billing/installments` with
`X-EIA-Tenant` and `X-EIA-Book: DIRECT`, the `tenantId` query scope, and the
optional `status` (`DUE` or `OVERDUE`) and inclusive `dueBefore` filters plus
the normalized `limit` (1..200, default 20). The expert returns
`{items, totalAmount, overdueAmount, count}` with items ordered by due date,
policy number, and installment number, each item carrying the plan, policy, and
insured-party context and a `daysPastDue` age. The domain recomputes totals,
count, and `daysPastDue` only when the expert omits them, so the public summary
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
`POST` sends `{amount, currency, reason?}`; `PATCH` sends `{status}`. The expert
returns the flat note `{creditNoteId, noteNumber, policyId, amount, currency,
reason, status, issuedAt, appliedAt, createdAt}`; the domain accepts the
expert's `id` identity and SPEC column aliases (`note_number`, `policy_id`,
`issued_at`, `applied_at`, `created_at`). `GET` returns the policy's notes
newest first, or an empty array when none exists. The expert owns the note
number, the `DRAFT`/`ISSUED`/`APPLIED`/`CANCELLED` lifecycle, the
policy-currency match, the optional 500-character reason, the approval gate,
and the SAP credit-memo reference, so an unknown currency is a preserved `400
CURRENCY_MISMATCH`, a missing policy or note is `404 POLICY_NOT_FOUND` /
`404 CREDIT_NOTE_NOT_FOUND`, and an out-of-order or terminal transition is
`409 INVALID_CREDIT_NOTE_TRANSITION`. The expert side of this contract lives in
`eia-policy-expert`; this repository contains the domain adapter only.

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

## Verification

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-22.0.2.9-hotspot'
.\gradlew.bat build
```

`PolicyContractControllerTest` covers every new route, `PolicyContractServiceTest`
covers page-link rebuilding and header validation, and `PolicyClientTest` covers
header forwarding and expert problem translation. `PolicyBillingControllerTest`
covers the three billing routes, validation, tenant/book handling, and problem
propagation; `PolicyBillingClientTest` uses `MockRestServiceServer` for the
forwarded tenant/book headers, payloads, the installment list, and preserved
`400`/`404`/`409` problems. `PolicyRenewalClientTest` uses
`MockRestServiceServer` for the renewal tenant/book headers, new-term payload,
successor policy and `renewedFromPolicyId` response, and preserved
`400`/`404`/`409` problems. `PolicyReceivableClientTest` uses
`MockRestServiceServer` for the receivable tenant/DIRECT headers,
status/dueBefore/limit query, projection and summary mapping, ageing fallback,
and preserved `400`/`404` problems; `PolicyReceivableControllerTest` covers the
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
tenant/DIRECT headers, the request and status payloads, the identity/column
aliases, the newest-first list, and preserved `400`/`404`/`409` problems;
`PolicyCreditNoteControllerTest` covers the list/request/status routes, shape
validation, tenant/book handling, and problem propagation, and
`PolicyCreditNoteServiceTest` covers the delegation.

## Known SPEC gaps in this slice

- The domain performs no independent business-rule validation for the second
  slice or billing; it validates request shape, page bounds, ETag syntax, and
  idempotency keys, then relies on the expert for the SPEC invariants.
- There is no transaction catalog or asynchronous orchestration; every command
  is a synchronous expert call.
- Approvals are represented by `approvedBy` / `approvalRef` identifiers supplied
  by the caller; no approval workflow service is integrated.
- The domain does not proxy occurrences, claims, reinsurance, billing accounts,
  billing instructions, disputes, commission statements, producer payables, or
  lineage projections (SPEC 11.2.10/11.2.13) yet.
