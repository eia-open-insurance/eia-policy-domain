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

## Local development

Requires JDK 22 and the Policy expert running on port 8083.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-22.0.2.9-hotspot'
.\gradlew.bat build
.\gradlew.bat bootRun
```

The service listens on port 8084. Every request requires `X-EIA-Tenant`.

- `GET /api/v1/policies`
- `GET /api/v1/policies/{id}`
- `POST /api/v1/policies`
- `PATCH /api/v1/policies/{id}/status`

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
