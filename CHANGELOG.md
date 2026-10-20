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
- Second slice: public proxies for policy versions, coverage/limits with
  aggregate erosion, endorsements, insureds/locations, cancellation, and
  reinstatement under `/api/v1/policies/{policyId}`.
- Page envelopes with public `_links` for every second-slice collection.
- Mutation responses echo the expert `ETag` and `Idempotent-Replayed` header;
  `If-Match` and `Idempotency-Key` are validated before the expert call.
- Expert `422 BUSINESS_RULE` and `428 APPROVAL_REQUIRED` problems pass through
  unchanged.
- `GET /api/v1/policies/search` with the shared global-search envelope
  `{query, limit, items}`, case-insensitive contains matching, tenant scoping,
  a 10-default / 25-capped limit, and a `/policy?record=<id>` href. The endpoint
  requires `X-EIA-Book: DIRECT` and returns `404 BOOK_SCOPE_VIOLATION` for any
  other book.
- Optional `insuredPartyId` UUID filter on `GET /api/v1/policies`. The filtered
  read is tenant-scoped, preserves the `updated_at DESC, id ASC` order and page
  envelope, carries the filter into `_links`, and returns `400 INVALID_PARAMETER`
  for a malformed UUID.

### Changed

- `GET /api/v1/policies` now returns the page envelope instead of a bare array.
- Illegal lifecycle transitions and duplicate policy numbers are `409` conflicts
  (the business codes `INVALID_STATUS_TRANSITION` and `DUPLICATE_POLICY_NUMBER`
  are unchanged).
- Expert failures preserve their status, code, field errors, and metadata.

### Removed

- The legacy `{timestamp, status, code, message, path, details}` body.
