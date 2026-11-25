# Registrations API quickstart: NHS API platform integration

## Before you start

We will ask you for:

- Your NHS login integration client ID. We only accept ID tokens issued to it.
- Your product ID, which tells us which supplier is calling. You may have one already, or you may need to set it.

## 1. Set up your application

On the [NHS API platform developer portal](https://onboarding.prod.api.platform.nhs.uk/), in the **Integration** environment:

1. Add a custom attribute `productid` to your application, set to your product ID.
2. Connect the API **HealthStore Digital Therapeutics Registrations - Patient Access (Integration)**. If there are two entries with that name, select both.

The API is at `https://int.api.service.nhs.uk/healthstore-dtx-registrations-aos`. `GET /_ping` returns 200 without authentication.

## 2. Get an access token

1. Sign the patient in with your NHS login client, in NHS login's integration environment (issuer `https://auth.aos.signin.nhs.uk`). The ID token must be P9.
2. Exchange the ID token at `https://int.api.service.nhs.uk/oauth2/token`, as described in the platform's [guide to token exchange with NHS login](https://digital.nhs.uk/developer/guides-and-documentation/security-and-authorisation/user-restricted-restful-apis-nhs-login-separate-authentication-and-authorisation). Each client assertion works only once, so make a new one for every request.

The access token lasts about 10 minutes and is tied to that patient.

## 3. Get the patient's registrations

```sh
curl --silent --include \
  --header 'Authorization: Bearer <ACCESS_TOKEN>' \
  --header "X-Request-ID: $(uuidgen)" \
  --header 'X-Synthetic-Registrations: false' \
  https://int.api.service.nhs.uk/healthstore-dtx-registrations-aos/registrations
```

> ⚠️ **Send `X-Synthetic-Registrations: false`.** Without it you get made-up test registrations, not the patient's real ones.

Expected result: HTTP 200 and a FHIR `searchset` Bundle of ServiceRequests, one for each registration you haven't acknowledged yet. An empty Bundle means there are none. The registration ID is the ServiceRequest `identifier` with system `https://fhir.dtx.national.nhs.uk/Id/registration`.

Use a new `X-Request-ID` for each request, and the same one when you retry it.

## 4. Acknowledge a registration

```sh
curl --silent --include \
  --header 'Authorization: Bearer <ACCESS_TOKEN>' \
  --header "X-Request-ID: $(uuidgen)" \
  --header 'Content-Type: application/fhir+json' \
  --data '{"resourceType":"Task","status":"accepted","intent":"order","businessStatus":{"coding":[{"system":"https://fhir.dtx.national.nhs.uk/CodeSystem/registration-business-status","code":"registered"}]}}' \
  https://int.api.service.nhs.uk/healthstore-dtx-registrations-aos/registrations/<REGISTRATION_ID>/tasks
```

Expected result: HTTP 200 with no body. The registration no longer comes back from step 3.

If the patient already had an account with you, add a `statusReason` with the code `already-registered` to the same Task:

```json
"statusReason": {
  "coding": [{ "system": "https://fhir.dtx.national.nhs.uk/CodeSystem/acknowledgement-reason", "code": "already-registered" }]
}
```

To reject a registration instead, send this Task. The reason code is `licence-exhausted`, `intervention-not-configured`, or `other` with a `note` saying why, for example `"note": [{ "text": "<why>" }]`.

```json
{
  "resourceType": "Task",
  "status": "rejected",
  "intent": "order",
  "businessStatus": {
    "coding": [{ "system": "https://fhir.dtx.national.nhs.uk/CodeSystem/registration-business-status", "code": "rejected" }]
  },
  "statusReason": {
    "coding": [{ "system": "https://fhir.dtx.national.nhs.uk/CodeSystem/acknowledgement-reason", "code": "licence-exhausted" }]
  }
}
```

## If something goes wrong

Contact us with your application name, the time of the request, the `X-Request-ID` you sent, and the HTTP status and response body. Never send your private key.
