# eia-policy-domain

- GitHub organization: `eia-open-insurance`
- Repository URL: https://github.com/eia-open-insurance/eia-policy-domain
- Type: Spring Boot service
- Deployable: Yes
- Owner: Policy
- Runtime: Java 22 and Spring Boot
- Data store: eia_policy (tx schema)
- Depends on: eia-policy-expert, eia-contracts, eia-platform/libs

## Purpose

Policy business rules, transaction catalog, orchestration, approvals, and APIs. Direct policy lifecycle, coverage, limits, billing, underwriting, rating, and capacity.

## Implemented slice

The domain API validates tenant scope, policy dates, non-negative premium and limit, and the SPEC status lifecycle before delegating persistence to the Policy expert through `RestClient`.

The second slice proxies the policy contract surface and keeps the public
conventions on every new route: page envelopes for collections, strong ETags on
mutations, and optional `If-Match` / `Idempotency-Key` where the expert command
supports them. Business rules stay in the expert so both services enforce one
rule set.

### Second-slice endpoints

All additive under `/api/v1/policies/{policyId}`:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/versions` | Version history |
| `GET` | `/versions/current` | Derived current version with `hashVerified` |
| `GET` | `/versions/{versionId}` | One version snapshot |
| `GET`/`POST` | `/coverages` | Coverage list / add coverage with limit schedule |
| `GET` | `/limits` | Limit schedules |
| `GET` | `/aggregate-state` | Erosion state per limit |
| `POST` | `/erosion` | Apply an idempotent erosion fact |
| `GET`/`POST` | `/endorsements` | Endorsement list / endorse |
| `GET` | `/endorsements/{endorsementId}` | One endorsement |
| `GET`/`POST` | `/insureds` | Insured list / add insured |
| `GET`/`POST` | `/locations` | Location list / add location |
| `GET` | `/cancellations` | Cancellation history |
| `POST` | `/cancel` | Cancel with computed return premium |
| `GET` | `/reinstatements` | Reinstatement history |
| `POST` | `/reinstate` | Reinstate with approval and premium evidence |
| `POST` | `/renew` | Renew into a successor term |
| `GET`/`POST` | `/billing` | Billing plan and installments / create the plan |
| `PATCH` | `/billing/installments/{installmentId}` | Record an installment's payment state |

The expert status, code, `category`, field errors, and `meta` are preserved, so
`422 BUSINESS_RULE`, `428 APPROVAL_REQUIRED`, and `409` conflicts reach the
caller unchanged.

### Billing plan

`GET /api/v1/policies/{policyId}/billing` returns the plan projection
`{planId, planNumber, policyId, totalPremium, currency, installmentCount,
frequency, firstDueDate, status, installments[]}`. `POST` creates the plan from
`{installmentCount, frequency, firstDueDate}` and returns `201` with the same
projection. `PATCH /billing/installments/{installmentId}` accepts
`{status, paymentId?}` and returns the updated installment
`{installmentId, installmentNumber, dueDate, amount, status, paidAt, paymentId}`.
The Policy expert owns the schedule, the amount split, and the payment-state
rules. The domain validates the request shape and the 1..24 installment window,
forwards `X-EIA-Tenant` and `X-EIA-Book: DIRECT` on every billing call, and maps
the expert's `{plan, installments}` view to the flat public projection. A
supplied non-DIRECT book is `404 BOOK_SCOPE_VIOLATION`, and expert `400`, `404`,
and `409` problems reach the caller unchanged.

### Policy renewal

`POST /api/v1/policies/{policyId}/renew` renews the policy into a successor
term. The body is `{effectiveDate, expirationDate, premiumAmount?,
premiumCurrency?, limitAmount?, limitCurrency?}`; omitted money overrides
default to the expiring term in the expert. The response is `201` with the
successor `PolicyDto`, whose `renewedFromPolicyId` points back to the expiring
policy, plus the expert `ETag`. The domain forwards `X-EIA-Tenant` and
`X-EIA-Book: DIRECT` on every renewal, rejects a supplied non-DIRECT book with
`404 BOOK_SCOPE_VIOLATION`, and preserves expert `400`, `404`, and `409`
problems unchanged.

### Filtering the policy list by insured party

`GET /api/v1/policies?insuredPartyId={partyId}&page=0&size=5` filters the
tenant-scoped list to one insured party. The response keeps the standard page
envelope; the filter is repeated in every `_links` href so paging never widens
the result set, and the order stays `updated_at DESC, id ASC`. A malformed UUID
is `400 INVALID_PARAMETER`. The unfiltered list keeps proxying the Policy expert
unchanged; the filtered read uses the tenant-scoped `eia_policy` data store
because the expert does not expose an insured-party filter.

### Global search

`GET /api/v1/policies/search?q={term}&limit=10` returns the common shell search
envelope `{query, limit, items}`. `q` is a case-insensitive contains over
`policyNumber` and `productCode`; a blank `q` returns the latest records by
`updatedAt DESC`. `limit` defaults to 10 and is capped at 25. The endpoint
requires `X-EIA-Tenant` and `X-EIA-Book: DIRECT`; a missing book is `400
BOOK_REQUIRED` and any other book is `404 BOOK_SCOPE_VIOLATION` before SQL
runs. The read uses `JdbcTemplate` against the `eia_policy` data store; the
existing routes keep proxying the Policy expert unchanged.

## Cross-cutting API behavior

The Policy domain is the reference implementation for the SPEC 6.11 / 12.2 API
conventions:

- **Errors** are `application/problem+json` with `type`, `title`, `status`,
  `detail`, `instance`, `code`, `category`, `retryable`, an optional
  `errors[{field, code, message}]` array, optional `meta`, and a correlation id.
  Existing business codes (`POLICY_NOT_FOUND`, `INVALID_STATUS_TRANSITION`,
  `VALIDATION_ERROR`, ...) are preserved.
- **Lists** return `{items, page, size, total, _links:{self,next,prev}}`.
  `page` defaults to 0, `size` defaults to 20 and is capped at 100; ordering is
  `updated_at DESC, id ASC`.
- **ETags** are the quoted aggregate version (`ETag: "3"`). `PATCH
  /{id}/status` accepts `If-Match`; a stale tag is `409
  CONCURRENT_MODIFICATION` with `meta.currentVersion` and a `STALE_VERSION`
  field error. `expectedVersion` in the body is still accepted for
  compatibility; `If-Match` takes precedence.
- **Idempotent create** accepts an optional `Idempotency-Key`. A replay returns
  the original policy with `200` and `Idempotent-Replayed: true`; the first
  create is `201` with `Idempotent-Replayed: false`. Reusing a key for a
  different request is `409 IDEMPOTENCY_CONFLICT`.

## Local development

Requires JDK 22 and the Policy expert running on port 8083.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-22.0.2.9-hotspot'
.\gradlew.bat build
.\gradlew.bat bootRun
```

The service listens on port 8084. Every request requires `X-EIA-Tenant`.

- `GET /api/v1/policies?page=0&size=20`
- `GET /api/v1/policies/{id}`
- `POST /api/v1/policies` (`Idempotency-Key` optional)
- `PATCH /api/v1/policies/{id}/status` (`If-Match` optional, takes precedence)
- `GET /api/v1/policies/{id}/versions`, `/versions/current`, `/coverages`,
  `/limits`, `/aggregate-state`, `/endorsements`, `/insureds`, `/locations`,
  `/cancellations`, `/reinstatements`
- `POST /api/v1/policies/{id}/coverages`, `/erosion`, `/endorsements`,
  `/insureds`, `/locations`, `/cancel`, `/reinstate`, `/renew`, `/billing`
- `PATCH /api/v1/policies/{id}/billing/installments/{installmentId}`

The expert base URL is configured with `eia.policy.expert-url` and defaults to `http://localhost:8083`.

## Responsibilities

- Keep this repository focused on one deployable component or one platform concern.
- Follow the repository standard in `../CONVENTIONS.md` and the specification in `../../SPEC.md`.
- Publish and consume contracts through `eia-contracts`.
- Maintain README, CODEOWNERS, SECURITY, CHANGELOG, tests, and local verification.
- Contain no business logic in shared libraries or front-end remotes.

## Verification

- Lint and format
- Unit tests with 100% business-logic coverage where applicable
- Contract tests
- Integration or Playwright tests where applicable
- Security scan and SBOM
- Build and publish

## Notes

Owns the business logic for this vertical.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
