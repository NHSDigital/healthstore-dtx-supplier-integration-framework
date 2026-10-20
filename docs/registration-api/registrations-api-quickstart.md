# NHS Health Store Registrations API quickstart

You need `curl`, `base64` and a Unix shell. The script also needs `uuidgen`.

Export these from the file we have sent you:

| Variable | What it is |
|---|---|
| `CLIENT_ID` | Your client ID |
| `CLIENT_SECRET` | Your client secret |
| `TOKEN_URL` | Where you get access tokens |
| `API_BASE_URL` | The base URL of the integration environment |

The contract is the Registrations API v1.0.0 in [`specification/healthstore-api.yaml`](../../specification/healthstore-api.yaml).

## Differences from the framework spec

The framework spec describes the target service. This is an interim integration environment, and differs in these ways:

- Use the API base URL above in place of the spec's `servers` URL. There is no `/registration/v1` prefix yet: the endpoint is `$API_BASE_URL/registrations`. A request to the wrong path gets a `403`.
- Use the token URL above. The spec's `tokenUrl` is not yet authoritative.
- Only retrieval works. `POST /registrations/{registration-id}/tasks` is not available yet.
- ID tokens are not checked yet (see step 2), so you will not get `TOKEN_EXPIRED`, `WRONG_AUDIENCE` or `PROOFING_BELOW_P9`.

## 1. Get an access token

Request a token:

```sh
curl --silent \
  --user "$CLIENT_ID:$CLIENT_SECRET" \
  --data 'grant_type=client_credentials&scope=registrations/access' \
  "$TOKEN_URL"
```

Expected response:

```json
{"access_token":"eyJ...","expires_in":600,"token_type":"Bearer"}
```

Copy the `access_token` value. It expires after 10 minutes; repeat this step for a new one.

## 2. Make a test ID token

> ⚠️ **For testing only, this environment accepts fake ID tokens.**
>
> We read the `nhs_number` claim from `NHSD-ID-Token` but do **not** check the signature, issuer, expiry, audience or identity level. Any well-formed JWT with a valid NHS number is accepted. This is so you can build against the API before a full NHS login integration is available.
>
> We will start checking ID tokens before go-live, and we will tell you before we do. After that, only a real NHS login ID token will work: signed by NHS login, unexpired, issued to your NHS login client, and at identity level P9. Do not build anything that relies on fake tokens.

In your product, you send the ID token NHS login gives you when the patient signs in, exactly as you received it. For now, you can make an unsigned token for one of these NHS login sandpit test users:

| Test user | NHS number | Sandpit login email | What you get back |
|---|---|---|---|
| Mona | 9686368973 | `testuserlive@demo.signin.nhs.uk` | 1 registration, with name, email and phone |
| Iain | 9686368906 | `testuserlive+1@demo.signin.nhs.uk` | 2 registrations, each from a different contracting organisation |
| Emilie | 9658220142 | `testuserlive+11@demo.signin.nhs.uk` | 1 registration, with no email or phone |
| Lee | 9661033404 | `testuserlive+17@demo.signin.nhs.uk` | 1 registration, with email but no phone |
| Mina | 9658218989 | `testuserlive+6@demo.signin.nhs.uk` | No registrations: an empty Bundle |

If you sign in to the NHS login sandpit as one of these users, you can send that real ID token instead and get the same result.

The registrations are **synthetic**: fixed for each test user, and the same every time. Any other valid NHS number returns an empty Bundle.

To make a token, replace Mona's NHS number with the one you want:

```sh
encode_base64url() { printf '%s' "$1" | base64 | tr '+/' '-_' | tr -d '=\n'; }
echo "$(encode_base64url '{"alg":"none","typ":"JWT"}').$(encode_base64url '{"nhs_number":"9686368973"}').unsigned"
```

## 3. Get the patient's registrations

Replace `<ACCESS_TOKEN>` with the value from step 1 and `<ID_TOKEN>` with a token from step 2. `X-Request-ID` must be a UUID; use a new one for each request, and the same one if you retry.

```sh
curl --silent --include \
  --header 'Authorization: Bearer <ACCESS_TOKEN>' \
  --header 'X-Request-ID: 3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b' \
  --header 'NHSD-ID-Token: <ID_TOKEN>' \
  "$API_BASE_URL/registrations"
```

Expected result for Mona: HTTP 200 and a search Bundle with one registration, as in the [example response](#example-response) at the end of this doc.

## Notes

- An empty Bundle is a normal result, not an error.
- The intervention in `ServiceRequest.code` is a placeholder for now: version `placeholder`, display `<PRODUCT> INTERVENTION NOT YET IMPLEMENTED`. We will replace it with your real intervention codes and tell you when we do. Do not map or store this value.
- This is a good time to test getting demographics from NHS login `/userinfo` if you are not doing so already. Building logic now that uses `/userinfo` as the primary source and the contained patient as a secondary fallback means no code changes will be needed when NHS login grants further scopes. We plan to cut the contained patient down to gender only. Until then it also carries name, email and phone.
- Repeated `GET /registrations` calls will return the same registrations for the same patient until acknowledged via the Lifecycle Tasks endpoint (not yet implemented).

## Errors

Errors are FHIR `OperationOutcome`s. Each issue has an error code in `details.coding[0].code`.

| HTTP | Code | Meaning |
|---|---|---|
| 401 | `NO_ACCESS` | Your access token is missing, expired or wrong (get a new one), or your client is not set up for this API (contact us) |
| 400 | `MISSING_HEADER` | `X-Request-ID` or `NHSD-ID-Token` is missing |
| 400 | `INVALID_VALUE` | A header is wrong, for example `X-Request-ID` is not a UUID or `NHSD-ID-Token` was sent twice |
| 401 | `TOKEN_INVALID` | The ID token is not a valid JWT, or its NHS number is not valid |
| 401 | `NHS_NUMBER_MISSING` | The ID token has no `nhs_number` claim |
| 429 | `TOO_MANY_REQUESTS` | Wait for the number of seconds in `Retry-After`, then retry |
| 503 | `SYSTEM_UNAVAILABLE` | A problem on our side. It is safe to retry |

## Or run the script

`try-registrations-api.sh` does all three steps for the NHS number you give it. Use one from the table in step 2. It reads the same environment variables as step 1, and exits with a non-zero status if either request fails.

```sh
sh try-registrations-api.sh '<NHS_NUMBER>'
```

## If something goes wrong

Contact us with:

- your client ID (never the secret)
- the time of the request
- the `X-Request-ID` you sent
- the HTTP status and response body
- the `x-amzn-requestid` response header (or `x-amz-cognito-request-id` if step 1 failed)

The script prints all of these except your client ID.

## Example response

The full response for Mona, from step 3. Values in angle brackets will differ for you.

```json
{
  "resourceType": "Bundle",
  "type": "searchset",
  "total": 1,
  "entry": [
    {
      "resource": {
        "resourceType": "ServiceRequest",
        "meta": {
          "profile": [
            "https://fhir.hl7.org.uk/StructureDefinition/UKCore-ServiceRequest"
          ]
        },
        "contained": [
          {
            "resourceType": "Patient",
            "id": "patient",
            "gender": "female",
            "name": [
              {
                "use": "official",
                "given": [
                  "Mona"
                ]
              }
            ],
            "telecom": [
              {
                "system": "email",
                "value": "testuserlive@demo.signin.nhs.uk",
                "use": "home"
              },
              {
                "system": "phone",
                "value": "+447887510887"
              }
            ]
          }
        ],
        "extension": [
          {
            "url": "https://fhir.dtx.national.nhs.uk/StructureDefinition/Extension-ContractingOrganisation",
            "valueIdentifier": {
              "system": "https://fhir.nhs.uk/Id/ods-organization-code",
              "value": "QWU"
            }
          }
        ],
        "identifier": [
          {
            "system": "https://fhir.dtx.national.nhs.uk/Id/registration",
            "value": "<REGISTRATION_ID>"
          }
        ],
        "status": "active",
        "intent": "order",
        "code": {
          "coding": [
            {
              "system": "https://fhir.dtx.national.nhs.uk/CodeSystem/intervention-<SUPPLIER>",
              "version": "placeholder",
              "code": "<PRODUCT>",
              "display": "<PRODUCT> INTERVENTION NOT YET IMPLEMENTED"
            }
          ]
        },
        "subject": {
          "reference": "#patient"
        }
      }
    }
  ]
}
```
