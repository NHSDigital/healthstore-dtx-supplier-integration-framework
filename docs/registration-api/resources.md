# Resources and paths

One surface, implemented by HealthStore and called by the platform. Every
call is made from inside the patient's NHS login session.

| Environment | Base URL |
|---|---|
| Sandpit | `https://api.sandpit.dtx.national.nhs.uk/registration/v1` |
| Integration | `https://api.{env}.dtx.national.nhs.uk/registration/v1`, environment name assigned at onboarding |
| Production | `https://api.{env}.dtx.national.nhs.uk/registration/v1`, environment name assigned at onboarding |

## Registrations API

| Method | Path | Request body | Returns |
|---|---|---|---|
| `POST` | `/registrations/retrieve` | `application/json`: `{ "id_token": "<the patient's NHS login ID token>" }` | `200` with a `searchset` Bundle of the patient's open registrations, `application/fhir+json`. Empty when there are none |
| `POST` | `/registrations/{registration-id}/tasks` | `application/fhir+json`: a lifecycle `Task`, `businessStatus` `registered` or `rejected`, `statusReason` coded | `200`, no body |

### Retrieve

The request carries the ID token and nothing else. The bearer token fixes the
supplier and the product; the ID token fixes the patient.

HealthStore verifies the ID token before returning anything:

| Check | Rule |
|---|---|
| Signature | JWS, verified against NHS login's published JWKS. Cached; refetched on an unknown `kid` |
| `iss` | The NHS login issuer for the environment. See the table below |
| `exp` | Not passed |
| `aud` | Equals the NHS login `client_id` recorded against the calling credential at onboarding. Never taken from the request |
| `identity_proofing_level` | `P9` |
| NHS number | Present in the token's claims |

A token may be presented more than once within its validity; there is no
single-use rule. An expired token draws `401` `TOKEN_EXPIRED`; the platform
re-authenticates the patient and retries with the new token.

Retrieval is read-only. A registration stays open until acknowledged and is
returned at every sign-in until then. A platform that retrieves and then fails
before acknowledging sees the registration again at the next sign-in.

#### NHS login issuers

Not a hard-coded list. Each HealthStore deployment pins the expected `iss` in
configuration and validates it at startup against that environment's
`/.well-known/openid-configuration`, so a wrong value fails at deploy rather
than at the first token.

| HealthStore environment | NHS login `iss` |
|---|---|
| Sandpit | `https://auth.sandpit.signin.nhs.uk` |
| Integration | Confirmed at onboarding |
| Production | `https://auth.login.nhs.uk` |

### Acknowledge

One Task per registration, sent after the platform's checks and its account
write, in the same sign-in. `registered` proves the sign-in, since the Task
can only be sent from inside the patient's session. A repeat Task for a
registration already acknowledged with the same `businessStatus` is a no-op
`200`. The codes and their rules are in [`fhir.md`](fhir.md).

## Authentication

One credential set, held by the platform, used on every call. The patient's
ID token is not a credential for the API; it is the content of the retrieve
request.

### Product identity is the credential

A platform with more than one product registers one platform application,
with its own client credentials, per product per environment. At onboarding
each client id is mapped to the supplier, the product, and the supplier's NHS
login `client_id` used to check the ID token's audience. A product's
credential can only retrieve and acknowledge its own registrations. Nothing
in the request names the product.

### Interim, until APIM onboarding completes: AWS Cognito

| Item | Detail |
|---|---|
| Register | Credentials issued by HealthStore, one set per product per environment |
| Grant | OAuth 2.0 `client_credentials` |
| Token endpoint | The auth provider's, not a path on the API. Issued with the credentials |
| Client authentication | `client_secret_post` |
| Token | Bearer, JWT |
| Scopes | None |

### Target: NHS England API Management

Application-restricted, signed JWT. The security scheme is defined at
`https://proxygen.prod.api.platform.nhs.uk/components/securitySchemes/app-level3`.

| Item | Detail |
|---|---|
| Register | NHS England developer portal |
| Grant | OAuth 2.0 `client_credentials` |
| Token endpoint | `/oauth2/token` |
| Client authentication | RS512-signed JWT assertion, `client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer` |
| Token | Bearer, JWT |
| Scopes | None |

Moving from the interim to the target changes where a token is obtained and
how, and nothing else. The API calls, the bearer header, token caching against
`expires_in`, refresh and `401` handling are the same throughout.

At the token endpoint, `client_id` and `client_secret` are replaced by a
`client_assertion`: a JWT with `iss` and `sub` set to the API key, `aud` set
to the token endpoint, a per-request `jti`, and `exp` no more than five minutes
ahead, signed RS512 with a private key whose public half is registered with NHS
England.

## Errors

Every error returns an `OperationOutcome`. Every code is classed terminal or
retryable.

| Failure | Status | Code | Class |
|---|---|---|---|
| Body is not valid FHIR or JSON | 400 | `INVALID_FHIR_STRUCTURE` | Terminal |
| A required element is absent, including `note` where `statusReason` is `other` | 400 | `MISSING_VALUE` | Terminal |
| An element is the wrong type or format | 400 | `INVALID_VALUE` | Terminal |
| An element has a value outside its value set, or a `statusReason` that does not fit the `businessStatus` | 400 | `INVALID_CODE` | Terminal |
| A required header is absent | 400 | `MISSING_HEADER` | Terminal |
| No bearer token, or one that is invalid or expired | 401 | `NO_ACCESS` | Terminal: obtain a new access token |
| ID token malformed, signature not verified, or `iss` wrong | 401 | `TOKEN_INVALID` | Terminal: re-authenticate the patient |
| ID token expired | 401 | `TOKEN_EXPIRED` | Terminal: re-authenticate the patient |
| ID token `aud` is not the client id registered for this credential | 401 | `WRONG_AUDIENCE` | Terminal: configuration; raise with onboarding |
| `identity_proofing_level` below P9 | 401 | `PROOFING_BELOW_P9` | Terminal: the patient needs a P9 NHS login |
| No NHS number in the token | 401 | `NHS_NUMBER_MISSING` | Terminal |
| The registration is not known, or is not this credential's | 404 | `REFERENCE_NOT_FOUND` | Terminal |
| Rate exceeded | 429 | `TOO_MANY_REQUESTS` | Retryable after `Retry-After` |
| Unavailable, or no response | 503, timeout | `SYSTEM_UNAVAILABLE` | Retryable with exponential backoff |

A retryable failure on retrieve costs nothing: retrieval is read-only, the
registration stays open, and it is returned at the next sign-in if the retry
budget is exhausted. A retryable failure on acknowledge is retried; if the
process is lost, the next sign-in re-presents the registration and the
platform resends `registered`.

## Rate limits

Limits are per-credential abuse guards, set generously above any plausible
sign-in rate; the values follow as a versioned update to this specification.
Exceeding a limit returns `429` with `Retry-After` in seconds, and the caller
MUST wait at least that long before retrying.

For context: on a previous NHS API the gateway default was found under load
to be roughly 5 requests per second unless explicitly configured. This API's
limits are configured explicitly and are not the gateway default.

## Headers

Every call.

| Header | Required | Rule |
|---|---|---|
| `X-Request-ID` | Yes | A GUID for this request. Traces a call in support. Returned in the response headers. A retry repeats the same value |
| `X-Correlation-ID` | No | Supplied by the caller to track a transaction across systems. The same value may appear on many calls belonging to one transaction, for example the retrieve and the acknowledgements of one sign-in. Returned unchanged |

## Platform-side notes

Not part of the contract; recorded so the design's monitoring is visible.

- HealthStore records the time of each retrieval and each acknowledgement per
  registration and monitors retrieved-but-never-acknowledged age as an alert.
  Read-only retrieval removes the deadline that would otherwise surface a
  platform that retrieves but never acknowledges, so the alert does that job.
- Registration status shown to the clinical team is derived from the same
  record: invited, not yet signed in, registered, rejected, expired.
