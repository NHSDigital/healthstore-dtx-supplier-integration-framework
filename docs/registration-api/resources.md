# Resources and paths

Two surfaces. Base paths are not yet set.

Every endpoint except `/oauth/token` carries `application/fhir+json`.

## Supplier API

Implemented by the platform, called by HealthStore.

| Method | Path | Carries | Returns |
|---|---|---|---|
| `POST` | `/oauth/token` | `client_credentials` grant, form-encoded | Bearer token |
| `POST` | `/healthstore-registration-requests` | A registration request `Task` | `200` with the Task, `status` set to `accepted` |

## Registrations API

Implemented by HealthStore, called by the platform.

| Method | Path | Carries | Returns |
|---|---|---|---|
| `GET` | `/registrations/{registration-id}` | | The `ServiceRequest` |
| `GET` | `/registrations?cohort={cohort-id}&_count={n}&page={p}` | | A `searchset` Bundle of ServiceRequests |
| `POST` | `/registrations/{registration-id}/tasks` | A lifecycle `Task`: accepted, rejected, activated | Not yet defined |

### Cohort retrieval

| Parameter | Required | Rule |
|---|---|---|
| `cohort` | Yes | The cohort identifier |
| `_count` | Yes | Page size, 1 to 100 |
| `page` | Yes | Page number, from 1 |

The Bundle carries `link` with `self`, `previous` and `next`.

Returns only registrations awaiting action from the platform: those with no
lifecycle Task outstanding against their current `ServiceRequest.status`,
whether that status is `active` or `revoked`. Registrations leave the worklist
as the platform actions them.

## Authentication

### Supplier API

Provided by the platform, used by HealthStore.

| Item | Detail |
|---|---|
| Grant | OAuth 2.0 `client_credentials` |
| Token endpoint | `/oauth/token`, unauthenticated |
| Client authentication | `client_secret_basic` or `client_secret_post` |
| Token | Bearer, JWT |
| Scopes | TBD |
| Credentials | Issued by the platform to HealthStore, rotated by the platform |

HealthStore holds one credential set per platform. How it stores and resolves
them is internal.

### Registrations API

NHS England API Management, application-restricted, signed JWT.
`https://proxygen.prod.api.platform.nhs.uk/components/securitySchemes/app-level3`

| Item | Detail |
|---|---|
| Register | NHS England developer portal |
| Grant | OAuth 2.0 `client_credentials` |
| Token endpoint | `/oauth2/token` |
| Client authentication | JWT signed with the platform's private key |
| Token | Bearer, JWT |
| Scopes | TBD |

An interim arrangement before APIM is not yet defined.

## Errors

Every endpoint except `/oauth/token` returns an `OperationOutcome` on error.
`/oauth/token` returns the OAuth 2.0 error response.

| Failure | Status | Code |
|---|---|---|
| Body is not valid FHIR | 400 | `INVALID_FHIR_STRUCTURE` |
| A required element is absent | 400 | `MISSING_VALUE` |
| An element is the wrong type or format | 400 | `INVALID_VALUE` |
| An element carries a value outside its value set | 400 | `INVALID_CODE` |
| A required header is absent | 400 | `MISSING_HEADER` |
| A required query parameter is absent | 400 | `MISSING_PARAMETER` |
| `code`, `focus` and `groupIdentifier` disagree | 422 | `CONFLICTING_VALUES` |
| The registration or cohort is not known, or is outside the caller's tenancy | 404 | `REFERENCE_NOT_FOUND` |
| No token, or a token that is invalid or expired | 401 | `ACCESS_DENIED` |
| Rate exceeded | 429 | `TOO_MANY_REQUESTS` |
| Unavailable | 503 | `SYSTEM_UNAVAILABLE` |

A platform that cannot accept a registration request at all responds with one of
the above rather than a Task.

## Headers

Every endpoint except `/oauth/token`.

| Header | Required | Rule |
|---|---|---|
| `X-Request-ID` | Yes | A GUID for this request. De-duplicates repeats and traces a call in support. Mirrored back in the response |
| `X-Correlation-ID` | No | Supplied by the caller to track a transaction across systems. Need not be unique per call. Returned unchanged |

A retry replays the request exactly, headers included, so both carry the same
values on every attempt.
