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
| `POST` | `/premium-audit` | Apply an audited premium; returns the policy and audit |
| `GET`/`POST` | `/billing` | Billing plan and installments / create the plan |
| `PATCH` | `/billing/installments/{installmentId}` | Record an installment's payment state |
| `POST` | `/billing/adjustment` | Append a positive premium adjustment installment |
| `GET`/`POST` | `/credit-notes` | Credit note list / request a credit note |
| `PATCH` | `/credit-notes/{creditNoteId}/status` | Advance a credit note's status |

The expert status, code, `category`, field errors, and `meta` are preserved, so
`422 BUSINESS_RULE`, `428 APPROVAL_REQUIRED`, and `409` conflicts reach the
caller unchanged.

Underwriting submissions are a top-level DIRECT-book collection:

| Method | Path | Purpose |
|---|---|---|
| `GET`/`POST` | `/api/v1/policies/submissions` | Submission list / create |
| `GET` | `/api/v1/policies/submissions/{submissionId}` | One submission |
| `PATCH` | `/api/v1/policies/submissions/{submissionId}/status` | Advance the status |

### Billing plan

`GET /api/v1/policies/{policyId}/billing` returns the plan projection
`{planId, planNumber, policyId, totalPremium, currency, installmentCount,
frequency, firstDueDate, status, installments[], summary}`. Each installment is
`{installmentId, installmentNumber, dueDate, amount, creditedAmount,
outstandingAmount, status, paidAt, paymentId}`: `amount` stays gross,
`creditedAmount` is premium covered by applied credit notes, and
`outstandingAmount` is the collectible remainder. Reversing an applied note
releases its credit, so `creditedAmount`, `outstandingAmount`, the installment
status, and the summary return to their pre-credit values without a shape
change. The `summary` is
`{totalAmount, paidAmount, waivedAmount, creditedAmount, outstandingAmount,
overdueAmount, currency, installmentCount}`, with `outstandingAmount` net of
paid, waived, and credited premium. `POST` creates the plan from
`{installmentCount, frequency, firstDueDate}` and returns `201` with the same
projection. `PATCH /billing/installments/{installmentId}` accepts
`{status, paymentId?}` and returns the updated installment. The Policy expert
owns the schedule, the amount split, the credit application, and the
payment-state rules. The domain validates the request shape and the 1..24
installment window, forwards `X-EIA-Tenant` and `X-EIA-Book: DIRECT` on every
billing call, and maps the expert's `{plan, installments, summary}` view to the
flat public projection (deriving a coherent summary when an older expert omits
it). A supplied non-DIRECT book is `404 BOOK_SCOPE_VIOLATION`, and expert `400`,
`404`, and `409` problems reach the caller unchanged.

`POST /billing/adjustment` accepts `{dueDate, reason}` and appends one
installment for the positive remainder between the policy written premium and
the ACTIVE plan total. The response is the
`{plan, installment, adjustmentAmount, newTotalPremium}` envelope: the updated
plan totals, the appended `Installment`, the derived adjustment amount, and the
new plan total. A fresh command returns `201`; an expert replay returns `200`
with `Idempotent-Replayed: true`. The domain forwards the tenant query scope,
`X-EIA-Tenant`, `X-EIA-Book: DIRECT`, the optional `If-Match`, and the optional
`Idempotency-Key`, echoes the expert `ETag`, and preserves expert `400`, `404`,
and `409` problems (for example `NO_PREMIUM_ADJUSTMENT`,
`BILLING_PLAN_NOT_FOUND`, `BILLING_PLAN_NOT_ACTIVE`, and
`MAX_INSTALLMENTS_REACHED`). An absent book is accepted; a supplied non-DIRECT
book is `404 BOOK_SCOPE_VIOLATION`.

### Premium audit

`POST /api/v1/policies/{policyId}/premium-audit` applies an audited premium to
a policy from `{auditedPremium, effectiveDate, reason, approvedBy?,
approvalRef?}`. `auditedPremium` must be non-negative with at most four
fraction digits, `effectiveDate` is required, and `reason` is required up to
500 characters. The response is the updated `PolicyDto` (including the new
`premiumAmount`, version, and any program/legal-entity links) when the expert
returns a bare policy, which is the current expert shape. When the expert also
returns an audit row, the response is `{policy, premiumAudit}` with
`premiumAudit` as `{auditId, policyId, auditedPremium, previousPremium,
premiumDelta, currency, effectiveDate, reason, approvedBy, approvalRef, status,
createdAt}`. A fresh application returns `201`; an expert replay is published as
`200` with `Idempotent-Replayed: true`. The domain forwards the
tenant query scope, `X-EIA-Tenant`, `X-EIA-Book: DIRECT`, the optional
`If-Match` tag, and the optional `Idempotency-Key`, echoes the expert `ETag`
(falling back to the returned policy's version), and preserves expert `400`,
`404`, and `409` problems. A supplied non-DIRECT book is `404
BOOK_SCOPE_VIOLATION`; an absent book is accepted for compatibility.

### Policy rating

`POST /api/v1/policies/{policyId}/rating` records one immutable rating
worksheet from `{ratingMethod, baseRate, exposureAmount?, factor?, apply?}` and
returns `201` with the `{worksheet, policy?}` envelope. `ratingMethod` is
`RATE_TIMES_EXPOSURE` (base rate × exposure × factor) or `FLAT` (base rate ×
factor); `baseRate` must be non-negative, `exposureAmount` is required and
strictly positive for `RATE_TIMES_EXPOSURE`, and `factor` defaults to `1` and
must be non-negative. `apply` defaults to `false`: the calculation is always
persisted, but only `apply: true` replaces the policy premium, bumps the
aggregate version, and emits `policy.policy.rated`. The applied response also
carries the refreshed `PolicyDto` and the expert ETag.

`GET` on the same path returns the newest worksheet: `{id, tenantId, policyId,
ratingMethod, baseRate, exposureAmount, factor, computedPremium, currency,
calcHash, status, createdAt, createdBy}`. `exposureAmount` is null for `FLAT`;
`computedPremium` is rounded once to four fraction digits; `calcHash` is the
canonical SHA-256 over the normalized inputs and result, so a later read can
reproduce the premium. The expert owns the methods, the DRAFT/QUOTED gate, the
calculation, the hash, the worksheet history, and the applied version; only a
`DRAFT` or `QUOTED` policy is rateable, otherwise `409 POLICY_NOT_RATEABLE`.
The domain validates the request shape, forwards `X-EIA-Tenant`,
`X-EIA-Book: DIRECT`, the optional `If-Match`, and the optional
`Idempotency-Key`, echoes the expert `ETag` (falling back to the applied
policy's version), and preserves expert `400`, `404`, and `409` problems. A
missing policy is `404 POLICY_NOT_FOUND`, a missing worksheet is `404
RATING_WORKSHEET_NOT_FOUND`, and a supplied non-DIRECT book is `404
BOOK_SCOPE_VIOLATION`.

### Underwriting submissions

`GET /api/v1/policies/submissions` returns the tenant's submissions as an array
of `{submissionId, submissionNumber, applicantPartyId, productId, programId,
requestedEffectiveDate, requestedLimits, requestedCoverages, tortCodes,
exposures, status, owner, receivedAt, updatedAt, version?}`. `requestedLimits`
and `exposures` are free-form JSON documents passed through from the expert, and
`version` is optional. `POST` creates a submission from `{submissionNumber?,
applicantPartyId, productId, programId?, requestedEffectiveDate?,
requestedLimits?, requestedCoverages?, tortCodes?, exposures?, owner?}` and
returns `201` with the stored projection; the expert numbers the submission when
`submissionNumber` is omitted. `GET
/api/v1/policies/submissions/{submissionId}` reads one submission, and `PATCH
/api/v1/policies/submissions/{submissionId}/status` advances it from
`{status, reason?}`. The expert owns the
`RECEIVED`/`CLEARANCE`/`RATING`/`REFERRED`/`QUOTED`/`BOUND`/`DECLINED`/`WITHDRAWN`
lifecycle, the clearance/referral/authority gates, the applicant and product
rules, and the quote/bind workflow. The domain forwards the tenant query,
`X-EIA-Tenant`, `X-EIA-Book: DIRECT`, the optional `Idempotency-Key`, and the
optional `If-Match`; an expert replay is `200` with `Idempotent-Replayed: true`;
the expert `ETag` is echoed (falling back to `version`); and expert `400`, `404`,
and `409` problems reach the caller unchanged. An absent book is accepted; a
supplied non-DIRECT book is `404 BOOK_SCOPE_VIOLATION`.

### Premium receivables

`GET /api/v1/billing/installments?status=&dueBefore=&limit=` lists the
tenant's premium receivables across policies. A receivable is one scheduled
installment enriched with its plan, policy, and insured-party context:
`{installmentId, planId, planNumber, policyId, policyNumber, insuredPartyId,
installmentNumber, dueDate, amount, creditedAmount, outstandingAmount,
currency, status, daysPastDue, paidAt, paymentId, lastDunningLevel,
lastDunningAt}`. `amount` stays the gross scheduled premium,
`creditedAmount` is the premium covered by applied credit notes, and
`outstandingAmount` is the collectible remainder. Reversing an applied note
releases the credit and restores the collectible amount without changing the
receivable shape. The response is the `{items,
totalAmount, overdueAmount, count}` summary, where `totalAmount` and
`overdueAmount` sum the outstanding amounts, oldest due date first, then policy
number and installment number. The domain recomputes those totals from the
outstanding amounts only when the expert omits them.

`status` narrows the effective installment state (`DUE` or `OVERDUE`); a stored
DUE installment past its due date is reported as OVERDUE without changing the
row. `PAID`/`WAIVED` installments are not on the worklist, so the expert rejects
those values with `400 INVALID_INSTALLMENT_STATUS`. `dueBefore` is an inclusive
due-date cutoff in ISO form; `limit` defaults to 20 and must be between 1 and
200. `daysPastDue` is zero unless the effective status is OVERDUE.
`lastDunningLevel` and `lastDunningAt` carry the most recent dunning action and
are absent until one is recorded. The route requires `X-EIA-Tenant`, accepts an
absent book for compatibility, rejects a supplied non-DIRECT book with `404
BOOK_SCOPE_VIOLATION`, and always forwards the tenant and `X-EIA-Book: DIRECT`
to the Policy expert. Expert `400` and `404` problems reach the caller
unchanged.

### Installment dunning

`POST /api/v1/billing/installments/{installmentId}/dunning` records a dunning
action from `{level, channel, notes?}` and returns `201` with the flat
`DunningAction` projection `{dunningId, installmentId, planId, policyId, level,
channel, notes, status, sentAt}`. `GET` on the same path returns the
installment's dunning history as an array of those projections, newest first,
and an empty array when nothing has been recorded. The Policy expert owns the
level and channel vocabularies, the collectible-state rule, and the
one-action-per-level constraint; an installment that is not DUE/OVERDUE is
`409 INSTALLMENT_NOT_DUNNABLE`, a repeated level is `409 DUNNING_ALREADY_SENT`,
and a missing or cross-tenant installment is `404 INSTALLMENT_NOT_FOUND`. The
domain validates the request shape, forwards `X-EIA-Tenant` and `X-EIA-Book:
DIRECT` on every call, and rejects a supplied non-DIRECT book with `404
BOOK_SCOPE_VIOLATION`. Expert `400`, `404`, and `409` problems reach the caller
unchanged.

### Policy credit notes

`GET /api/v1/policies/{policyId}/credit-notes` returns the policy's credit
notes newest first as an array of the flat projection `{creditNoteId,
noteNumber, policyId, amount, currency, reason, status, issuedAt, appliedAt,
appliedInstallmentId, createdAt, reversedAt, reversalReason, cancellationId,
cancellationNumber}`; `amount` is a scale-preserving decimal string and
`appliedInstallmentId` names the installment an APPLIED or REVERSED note was
credited against. A cancellation-linked note also carries `cancellationId` and
the expert's human-readable `cancellationNumber`; both are absent for every
other note. `POST` requests a note from `{amount, currency, reason?,
cancellationId?}` and returns `201` with the stored projection; the note starts
in the expert-owned `DRAFT` state, `currency` must match the policy premium
currency, and `reason` is optional and capped at 500 characters. The optional
`cancellationId` links the note to the cancellation whose return premium it
credits; the expert validates that the cancellation belongs to the
tenant/policy and is eligible for a credit note. `PATCH
/credit-notes/{creditNoteId}/status` accepts `{status, installmentId?,
reason?}` and returns the updated projection, with `issuedAt` set on `ISSUED`,
and `appliedAt` plus `appliedInstallmentId` set on `APPLIED`. `installmentId`
is required for `APPLIED` and is forwarded only for that transition; ISSUED and
CANCELLED keep the unchanged `{status}` payload. `REVERSED` is reachable from
`APPLIED`, requires a non-blank `reason` capped at 500 characters, and returns
`reversedAt` plus `reversalReason` while keeping the original application facts
for audit. The reason is forwarded only for `REVERSED`, and the reversal
releases the credit from the applied installment, so billing and receivables
keep their existing shape and their credited/outstanding amounts return to
their pre-credit values. The expert owns the note number, the
`DRAFT`/`ISSUED`/`APPLIED`/`CANCELLED`/`REVERSED` lifecycle, the approval gate,
the transition rules, the same-tenant/policy open-installment credit rule, the
cancellation-link rules, the reversal credit release, and the SAP credit-memo
reference. The domain validates the request shape, forwards `X-EIA-Tenant` and
`X-EIA-Book: DIRECT` on every call, and rejects a supplied non-DIRECT book with
`404 BOOK_SCOPE_VIOLATION`. Expert `400`, `404`, and `409` problems reach the
caller unchanged; a missing policy is `404 POLICY_NOT_FOUND`, a missing note is
`404 CREDIT_NOTE_NOT_FOUND`, a missing or foreign installment is `404
INSTALLMENT_NOT_FOUND`, a missing reversal reason is `400
INVALID_CREDIT_NOTE_REASON`, an out-of-order or terminal transition is `409
INVALID_CREDIT_NOTE_TRANSITION`, and a missing or ineligible cancellation link
keeps its expert `400`/`404`/`409` problem.

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

### Mass tort profile

`GET /api/v1/policies/{policyId}/mass-tort-profile` returns the profile
projection `{profileId, policyId, tortType, triggerType, allocationMethod,
allocationNotes, coverageBlockStart, coverageBlockEnd, status, updatedAt}`.
`PUT` replaces the profile from `{tortType, triggerType, allocationMethod,
allocationNotes?, coverageBlockStart?, coverageBlockEnd?, status}` and returns
the stored projection. A profile is `DRAFT` until it is complete; moving it to
`COMPLETE` requires the allocation notes and both coverage-block dates, and the
block is an inclusive date range whose end cannot precede its start. The Policy
expert owns the tort, trigger, allocation, and status vocabularies plus the
completeness rule. The domain validates the request shape, forwards
`X-EIA-Tenant` and `X-EIA-Book: DIRECT` on every call, and rejects a supplied
non-DIRECT book with `404 BOOK_SCOPE_VIOLATION`. Expert `400`, `404`, and `409`
problems reach the caller unchanged; a policy without a profile is
`404 MASS_TORT_PROFILE_NOT_FOUND`.

### Programs

`POST /api/v1/programs` creates a program from `{name, programType,
effectiveFrom, effectiveTo, leadPolicyId?, capacityAmount?,
capacityCurrency?}` and returns `201` with the flat projection `{programId,
programNumber, name, programType, effectiveFrom, effectiveTo, status,
leadPolicyId, capacityAmount, capacityCurrency, usedAmount, utilizationPct,
policyCount, createdAt}`. The program number and status are server-owned.
`capacityAmount` and `capacityCurrency` are an optional pair: omitting both
records a program without a capacity limit, while supplying one requires a
positive amount and a three-letter upper-case ISO 4217 currency. The derived
`usedAmount`, `utilizationPct`, and `policyCount` members aggregate the
policies assigned to the program; the expert owns that derivation and the
domain publishes the values unchanged. Amounts and the percentage are decimal
strings. `GET /api/v1/programs` returns the tenant's programs as an array
ordered newest first, and `GET /api/v1/programs/{programId}` returns one
program. `PATCH /api/v1/policies/{policyId}/program` accepts `{programId}` and
returns the updated `PolicyDto`; a null or absent `programId` clears the
policy's program. The `PolicyDto` projection now exposes `programId` on policy
reads and writes.

The Policy expert owns program numbering, the program-type and status
vocabularies, tenant-scoped uniqueness, the effective-date rules, the
policy-membership and lead-policy rules, and the capacity aggregates. The
domain validates the request shape, the effective-date ordering, and the
capacity amount/currency pair, forwards `X-EIA-Tenant` and `X-EIA-Book:
DIRECT` on every call, and rejects a supplied non-DIRECT book with
`404 BOOK_SCOPE_VIOLATION`. Expert `400`, `404`, and `409` problems reach the
caller unchanged; a missing program is `404 PROGRAM_NOT_FOUND`. A capacity pair
with one member missing is a `400 VALIDATION_ERROR` with
`CAPACITY_AMOUNT_REQUIRED` or `CAPACITY_CURRENCY_REQUIRED` on the missing
field.

### Legal entities

`POST /api/v1/legal-entities` registers a legal entity from `{code, name,
entityType, jurisdiction, regulator?, licenseNumber?}` and returns `201` with
the flat projection `{legalEntityId, code, name, entityType, jurisdiction,
regulator, licenseNumber, status, createdAt}`. `GET /api/v1/legal-entities`
returns the tenant's legal entities as an array, and
`GET /api/v1/legal-entities/{legalEntityId}` returns one.
`PATCH /api/v1/policies/{policyId}/legal-entity` accepts `{legalEntityId}`,
assigns the policy's issuing legal entity, and returns the updated `PolicyDto`
with its expert `ETag`; every policy must reference one issuer (SPEC LE-01), so
the assignment cannot be cleared.

The Policy expert owns tenant-scoped entity-code uniqueness, the entity-type
and status vocabularies, the regulator and license rules, and the policy
issuer and capacity rules. The domain validates the request shape, forwards
`X-EIA-Tenant` and `X-EIA-Book: DIRECT` on every call, and rejects a supplied
non-DIRECT book with `404 BOOK_SCOPE_VIOLATION`. Expert `400`, `404`, and `409`
problems reach the caller unchanged; a missing legal entity is
`404 LEGAL_ENTITY_NOT_FOUND`, a duplicate code is
`409 DUPLICATE_LEGAL_ENTITY_CODE`, and a stale policy update is
`409 CONCURRENT_MODIFICATION`. `PolicyDto` now exposes `legalEntityId` on
policy reads and writes.

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
- `GET`/`PUT /api/v1/policies/{id}/mass-tort-profile`
- `POST`/`GET /api/v1/programs`, `GET /api/v1/programs/{programId}`
- `PATCH /api/v1/policies/{policyId}/program`
- `POST`/`GET /api/v1/legal-entities`, `GET /api/v1/legal-entities/{legalEntityId}`
- `PATCH /api/v1/policies/{policyId}/legal-entity`
- `POST /api/v1/policies/{id}/coverages`, `/erosion`, `/endorsements`,
  `/insureds`, `/locations`, `/cancel`, `/reinstate`, `/renew`, `/billing`,
  `/billing/adjustment`, `/rating`
- `GET /api/v1/policies/{id}/rating`
- `GET`/`POST /api/v1/policies/submissions`,
  `GET /api/v1/policies/submissions/{submissionId}`,
  `PATCH /api/v1/policies/submissions/{submissionId}/status`
- `PATCH /api/v1/policies/{id}/billing/installments/{installmentId}`
- `GET /api/v1/billing/installments?status=&dueBefore=&limit=`
- `GET`/`POST /api/v1/billing/installments/{installmentId}/dunning`

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
