# Implementation notes

## Status

The Policy domain proxies both Policy slices. The first slice owns tenant
validation, create/status orchestration, and the public problem envelope. The
second slice adds pass-through routes for versions, coverage and limits,
aggregate erosion, endorsements, insureds and locations, cancellation,
reinstatement, and renewal into a successor term. Billing adds the plan and
installment schedule projection under the same tenant and DIRECT-book context.
A cross-policy premium-receivable read projects installments as receivables for
the collections/ageing view.

## Structure

| Class | Responsibility |
|---|---|
| `PolicyController` / `PolicyService` | First-slice public routes and lifecycle |
| `PolicyContractController` / `PolicyContractService` | Second-slice public routes, renewal, page link rebuild, header validation |
| `PolicyBillingController` / `PolicyBillingService` | Billing plan and installment routes; DIRECT-book guard |
| `ReceivableController` / `ReceivableService` | Cross-policy `GET /api/v1/billing/installments`; status/limit normalization |
| `BillingPlan`, `Installment` | Public billing projections |
| `Receivable`, `ReceivableSummary` | Public receivable projection and its totals |
| `RenewPolicyCommand` | Renewal request body; optional premium and limit overrides |
| `PolicyClient` | Anti-corruption boundary to the expert; translates expert problems |
| `PolicyQueryRepository` | Tenant-scoped direct read for the `insuredPartyId` list filter |
| `PolicyLifecycle` | Status transition table |
| `ItemPage`, `Mutation`, `EntityTags`, `Pagination`, `PageLinks` | Shared response and concurrency helpers |

## Premium-receivable expert contract

The domain forwards `GET /internal/v1/billing/installments` with
`X-EIA-Tenant` and `X-EIA-Book: DIRECT`, the `tenantId` query scope, and the
optional `status` and `dueBefore` filters plus the normalized `limit`. The
expert returns `{items, totalAmount, overdueAmount, count}` with items ordered
by due date and installment number, each item carrying the plan, policy, and
insured-party context and a `daysPastDue` age. The domain recomputes totals,
count, and `daysPastDue` only when the expert omits them, so the public summary
stays coherent during rollout. The expert owns that projection; this repository
contains the domain side of the contract only.

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

## Known SPEC gaps in this slice

- The domain performs no independent business-rule validation for the second
  slice or billing; it validates request shape, page bounds, ETag syntax, and
  idempotency keys, then relies on the expert for the SPEC invariants.
- There is no transaction catalog or asynchronous orchestration; every command
  is a synchronous expert call.
- Approvals are represented by `approvedBy` / `approvalRef` identifiers supplied
  by the caller; no approval workflow service is integrated.
- The domain does not proxy the mass tort profile, occurrences, claims,
  reinsurance, billing accounts, billing instructions, credit notes, dunning,
  disputes, commission statements, producer payables, or lineage projections
  (SPEC 11.2.10/11.2.13) yet.
