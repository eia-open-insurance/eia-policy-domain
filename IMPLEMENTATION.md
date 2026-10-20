# Implementation notes

## Status

The Policy domain proxies both Policy slices. The first slice owns tenant
validation, create/status orchestration, and the public problem envelope. The
second slice adds pass-through routes for versions, coverage and limits,
aggregate erosion, endorsements, insureds and locations, cancellation, and
reinstatement. Billing adds the plan and installment schedule projection under
the same tenant and DIRECT-book context.

## Structure

| Class | Responsibility |
|---|---|
| `PolicyController` / `PolicyService` | First-slice public routes and lifecycle |
| `PolicyContractController` / `PolicyContractService` | Second-slice public routes, page link rebuild, header validation |
| `PolicyBillingController` / `PolicyBillingService` | Billing plan and installment routes; DIRECT-book guard |
| `BillingPlan`, `Installment` | Public billing projections |
| `PolicyClient` | Anti-corruption boundary to the expert; translates expert problems |
| `PolicyQueryRepository` | Tenant-scoped direct read for the `insuredPartyId` list filter |
| `PolicyLifecycle` | Status transition table |
| `ItemPage`, `Mutation`, `EntityTags`, `Pagination`, `PageLinks` | Shared response and concurrency helpers |

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
`400`/`404`/`409` problems.

## Known SPEC gaps in this slice

- The domain performs no independent business-rule validation for the second
  slice or billing; it validates request shape, page bounds, ETag syntax, and
  idempotency keys, then relies on the expert for the SPEC invariants.
- There is no transaction catalog or asynchronous orchestration; every command
  is a synchronous expert call.
- Approvals are represented by `approvedBy` / `approvalRef` identifiers supplied
  by the caller; no approval workflow service is integrated.
- The domain does not proxy the mass tort profile, occurrences, claims,
  reinsurance, producer payables, or lineage projections (SPEC 11.2.10/11.2.13)
  yet.
