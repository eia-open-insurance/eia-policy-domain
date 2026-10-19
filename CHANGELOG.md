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

### Changed

- `GET /api/v1/policies` now returns the page envelope instead of a bare array.
- Illegal lifecycle transitions and duplicate policy numbers are `409` conflicts
  (the business codes `INVALID_STATUS_TRANSITION` and `DUPLICATE_POLICY_NUMBER`
  are unchanged).
- Expert failures preserve their status, code, field errors, and metadata.

### Removed

- The legacy `{timestamp, status, code, message, path, details}` body.
