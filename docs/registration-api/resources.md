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
| `GET` | `/registrations?_count={n}&cohort={cohort-id}` | | A `searchset` Bundle of ServiceRequests |
| `POST` | `/registrations/{registration-id}/tasks` | A lifecycle `Task`: registered, rejected, activated, deactivated. A rejection gives a reason in `statusReason` | Not yet defined |

### Worklist retrieval

| Parameter | Required | Rule |
|---|---|---|
| `_count` | Yes | Maximum registrations returned by one call, 1 to 100 |
| `cohort` | No | Narrows to one cohort. Omitted, the worklist covers every registration awaiting the platform's action |

The Bundle carries `link` with `self`.

The worklist is scoped to the calling platform, and returns only registrations
awaiting its action: those with no lifecycle Task outstanding against their
current `ServiceRequest.status`, whether that status is `active` or `revoked`.
Registrations leave the worklist as the platform actions them. The platform
repeats the call until no registrations are returned.

## Authentication

### Supplier API

Provided by the platform, used by HealthStore.

| Item | Detail |
|---|---|
| Grant | OAuth 2.0 `client_credentials` |
| Token endpoint | `/oauth/token`, unauthenticated |
| Client authentication | `client_secret_post` |
| Token | Bearer, JWT |
| Scopes | None |
| Credentials | Issued by the platform to HealthStore, rotated by the platform |

HealthStore holds one credential set per platform. How it stores and resolves
them is internal.

### Registrations API

Interim, until APIM onboarding completes. AWS Cognito.

| Item | Detail |
|---|---|
| Register | Credentials issued by HealthStore |
| Grant | OAuth 2.0 `client_credentials` |
| Token endpoint | `/oauth2/token`, on the same host as the API. The auth provider's endpoint, carried in the spec only as `tokenUrl` |
| Client authentication | `client_secret_post` |
| Token | Bearer, JWT |
| Scopes | TBD |

Target. NHS England API Management, application-restricted, signed JWT.
`https://proxygen.prod.api.platform.nhs.uk/components/securitySchemes/app-level3`

| Item | Detail |
|---|---|
| Register | NHS England developer portal |
| Grant | OAuth 2.0 `client_credentials` |
| Token endpoint | `/oauth2/token` |
| Client authentication | RS512-signed JWT assertion, `client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer` |
| Token | Bearer, JWT |
| Scopes | TBD |

Moving from the interim to the target changes how a token is obtained and
nothing else. The API calls, the bearer header, token caching against
`expires_in`, refresh and `401` handling are the same throughout.

At the token endpoint, `client_id` and `client_secret` are replaced by a
`client_assertion`: a JWT carrying `iss` and `sub` set to the API key, `aud` set
to the token endpoint, a per-request `jti`, and `exp` no more than five minutes
ahead, signed RS512 with a private key whose public half is registered with NHS
England.

## Errors

Every endpoint except the token endpoints returns an `OperationOutcome` on
error. The token endpoints, `/oauth/token` on the supplier API and
`/oauth2/token` on the Registrations API, return the OAuth 2.0 error response.

| Failure | Status | Code |
|---|---|---|
| Body is not valid FHIR | 400 | `INVALID_FHIR_STRUCTURE` |
| A required element is absent | 400 | `MISSING_VALUE` |
| An element is the wrong type or format | 400 | `INVALID_VALUE` |
| An element carries a value outside its value set | 400 | `INVALID_CODE` |
| A required header is absent | 400 | `MISSING_HEADER` |
| A required query parameter is absent | 400 | `MISSING_PARAMETER` |
| The registration or cohort is not known, or is outside the caller's tenancy | 404 | `REFERENCE_NOT_FOUND` |
| No token, or a token that is invalid or expired | 401 | `NO_ACCESS` |
| Rate exceeded | 429 | `TOO_MANY_REQUESTS` |
| Unavailable | 503 | `SYSTEM_UNAVAILABLE` |

A registration request whose `code`, `focus` and `groupIdentifier` disagree
fails the request schema and draws a 400.

A platform that cannot accept a registration request at all responds with one of
the above rather than a Task.

## Rate limits

| Surface | Limit set by | Value |
|---|---|---|
| Registrations API | NHS HealthStore | TBC |
| Supplier API | The platform | TBC |

Whether a limit counts per endpoint or across a surface is TBC.

Exceeding the limit returns `429` with `Retry-After` in seconds. The caller MUST
wait at least that long before retrying.

## Headers

Every endpoint except `/oauth/token`.

| Header | Required | Rule |
|---|---|---|
| `X-Request-ID` | Yes | A GUID for this request. De-duplicates repeats and traces a call in support. Mirrored back in the response |
| `X-Correlation-ID` | No | Supplied by the caller to track a transaction across systems. Need not be unique per call. Returned unchanged |

A retry replays the request exactly, headers included, so both carry the same
values on every attempt.
