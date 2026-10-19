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
mutations, optional `If-Match`, and optional `Idempotency-Key` with
`Idempotent-Replayed`. Business rules stay in the expert so both services
enforce one rule set.

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

The expert status, code, `category`, field errors, and `meta` are preserved, so
`422 BUSINESS_RULE`, `428 APPROVAL_REQUIRED`, and `409` conflicts reach the
caller unchanged.

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
  `/insureds`, `/locations`, `/cancel`, `/reinstate`

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
