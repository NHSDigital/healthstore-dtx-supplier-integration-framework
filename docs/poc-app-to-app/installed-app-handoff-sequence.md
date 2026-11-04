# App-to-app handoff — installed app (DTX-905)

Sequence for launching a patient from the HealthStore patient service into the
installed my mhealth app, signed in, with a single NHS Login. Two asserted
identity issuances, one per hop: NHS App asserts into HealthStore (hop A),
HealthStore asserts into my mhealth (hop B). Trust stays in NHS Login: each
assertion is a 60-second one-time correlation token, verified by NHS Login
against the registered relying-party relationship before any code is issued.

```mermaid
sequenceDiagram
  actor Patient
  participant NHSApp as NHS App (native)
  participant HealthStore as HealthStore patient service (RP1)
  participant NHSLogin as NHS Login
  participant MMH as my mhealth (RP2, installed)

  Note over Patient,NHSLogin: Stage 1 — into HealthStore (hop A, NHS App's token)
  Patient->>NHSApp: Open NHS App / login (NHS Login P9)
  Patient->>NHSApp: Open HealthStore (webview)
  NHSApp->>HealthStore: /api/sso?assertedLoginIdentity=JWT (NHS App mints off its id_token)
  HealthStore->>NHSLogin: /authorize + asserted_login_identity (client nhs-dtx)
  NHSLogin-->>HealthStore: code (no login form)
  HealthStore->>NHSLogin: /token (private_key_jwt)
  NHSLogin-->>HealthStore: id_token — HealthStore session, jti retained
  Note over Patient,HealthStore: Patient is in our screens inside the NHS App

  Note over Patient,MMH: Stage 2 — handoff to partner (hop B, HealthStore's token)
  Patient->>HealthStore: "Open my mhealth" (/api/handoff?app=mycopd)
  HealthStore->>HealthStore: Mint asserted_login_identity<br/>(iss=nhs-dtx, code=own id_token jti, RS512, 60s, one-time)
  HealthStore-->>Patient: 302 https://dev.mymhealth.com/login?asserted_login_identity=JWT
  Note over Patient,MMH: Universal link claimed by installed app —<br/>OS opens my mhealth app (web page is the fallback)
  Patient->>MMH: Launch with asserted_login_identity
  MMH->>NHSLogin: /authorize + asserted_login_identity (client mmh, via redirect Lambda + Cognito)
  NHSLogin->>NHSLogin: Verify RP1 signature + registered nhs-dtx→mmh relationship + jti correlation
  NHSLogin-->>MMH: code to registered redirect (Cognito idpresponse) — no login form
  MMH->>MMH: Cognito exchanges code for tokens
  MMH-->>Patient: Signed in as the same NHS user
```

## Notes

- **Assertion shape (both hops)** — NHS Login EIS, authorize request
  extensions: `iss` = minting RP's client_id, `code` = the `jti` of the
  minting RP's own NHS Login id_token, `jti`, `iat`, `exp` ≤ 60s, signed with
  the minting RP's client private key. One-time. No identity claims cross the
  hop; NHS Login resolves the user from the correlated login.
- **Parameter casing** — camelCase `assertedLoginIdentity` on service edges
  (NHS App convention); snake_case `asserted_login_identity` on NHS Login's
  `/authorize`.
- **Relationship config** — RP1→RP2 SSO is enabled by NHS Login per client
  pair (sandpit: `nhs-dtx` → `mmh`, in place and validated).
- **Web fallback** — if the universal link is not claimed on the device, the
  same URL serves my mhealth's web login and the flow completes in the
  browser/webview. This is the variant proven so far.

## Status

| Leg | Status |
|---|---|
| Hop B, web variant (HealthStore web → my mhealth web, code via Cognito) | Proven 2026-06-10, sandpit, real pair |
| Hop A (NHS App → HealthStore `/api/sso`) | Built; to exercise with the NHS App build |
| Hop B, installed variant (universal link opens the installed app) | Pending my mhealth sandbox build with claimed link |
| Webview behaviour (universal-link app switch from inside the NHS App webview) | To prove with the installed variant |
