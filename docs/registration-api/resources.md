# Resources and paths

Two surfaces. Base paths are not yet set.

## Supplier API

Implemented by the platform, called by HealthStore.

| Method | Path | Carries | Returns |
|---|---|---|---|
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

## Errors

Every error returns an `OperationOutcome`.

| Failure | Status | Code |
|---|---|---|
| Body is not valid FHIR | 400 | `INVALID_FHIR_STRUCTURE` |
| A required element is absent | 400 | `MISSING_VALUE` |
| An element is the wrong type or format | 400 | `INVALID_VALUE` |
| An element carries a value outside its value set | 400 | `INVALID_CODE` |
| A required header is absent | 400 | `MISSING_HEADER` |
| A required query parameter is absent | 400 | `MISSING_PARAMETER` |
| `code`, `focus` and `groupIdentifier` disagree | 422 | `CONFLICTING_VALUES` |
| The registration or cohort is not known | 404 | `REFERENCE_NOT_FOUND` |
| The caller is not scoped to that registration | 403 | `NO_RELATIONSHIP` |
| Rate exceeded | 429 | |
| Unavailable | 503 | `SYSTEM_UNAVAILABLE` |

A platform that cannot accept a registration request at all responds with one of
the above rather than a Task.

## Headers

Both surfaces, every call.

| Header | Required | Rule |
|---|---|---|
| `X-Request-ID` | Yes | A GUID for this request. De-duplicates repeats and traces a call in support. Mirrored back in the response |
| `X-Correlation-ID` | No | Supplied by the caller to track a transaction across systems. Need not be unique per call. Returned unchanged |

A retry replays the request exactly, headers included, so both carry the same
values on every attempt.
