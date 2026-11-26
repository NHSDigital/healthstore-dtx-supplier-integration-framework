# Supplier Integration Test cases SIT-01 to SIT-19

These are the cases the exit criteria in the [Supplier Integration Test Plan](https://nhsd-confluence.digital.nhs.uk/spaces/DTX/pages/1561338739):

- Each supplier passes SIT-01 to SIT-09, with evidence.
- Each supplier passes SIT-10 and SIT-12 to SIT-17 for at least one patient.
- SIT-11, SIT-18 and SIT-19 are included if their dependencies are ready.

SIT-01 to SIT-09 are the Stage 2 pack (T-AUTH, T-GET, T-POST, T-SUP) grouped into nine things a supplier signs off, so nothing there is new to suppliers. SIT-10 to SIT-19 are the Stage 3 end-to-end day on 1 December, with real registrations.

## Before Stage 3

- Every supplier has passed Stage 2.
- The supplier sends `X-Synthetic-Registrations: false`. Without it they keep getting the synthetic set and never see the patient the clinician enrolled.
- The test patient has the same NHS number in NHS login sandpit, in the PDS that Int uses, and in NHS Notify.
- Tasks are recorded, so an acknowledged registration stops being returned.
- The supplier's NHS login sandpit client works with Int.

## Stage 2 cases, every supplier (SIT-01 to SIT-09)

| ID | Case | Pack cases covered | Expected | Evidence |
| --- | --- | --- | --- | --- |
| SIT-01 | Get a token | T-AUTH-01 to T-AUTH-03 | `200` and a bearer token with `expires_in`. A wrong secret and a scope the client may not use are refused with `400` | Request and response for each |
| SIT-02 | Retrieve registrations at sign-in | T-GET-01, T-GET-02, T-GET-09, T-SUP-01 | The supplier calls `GET /registrations` on every NHS login sign-in, straight after its own `/userinfo` call. `200`, one registration for Mona, returned again until acknowledged. The `X-Request-ID` sent comes back unchanged | Request and response, with the `/userinfo` call before it in the log |
| SIT-03 | Handle every registration shape | T-GET-03 to T-GET-08, T-GET-10 | Two registrations (Iain), no contact details (Emilie), email only (Lee), one per intervention (Amanda), second intervention only (Kevin) and an empty Bundle (Mina) are all handled. No Task is sent for an empty Bundle. Elements the supplier does not recognise are ignored and the registration is still processed | One log per patient |
| SIT-04 | Report registered | T-POST-01, T-POST-02, T-POST-06, T-SUP-02 | One Task per open registration, in the same sign-in, each with that entry's registration id. `registered`, and `registered` with `already-registered`, are accepted. Sending the same outcome again returns `200` both times | Each request and `200` response |
| SIT-05 | Report rejected | T-POST-03 to T-POST-05, T-POST-07 to T-POST-11 | `licence-exhausted`, `intervention-not-configured` and `other` with a note are accepted. The supplier never sends the refused combinations: `other` with no note (`400`), a rejection with no reason, a reason with `registered`, or `already-registered` with `rejected` | Each accepted request and `200` response. A statement of how the supplier avoids the refused ones |
| SIT-06 | Handle refused GETs | T-GET-11 to T-GET-18, T-SUP-05 | The supplier does not send the same broken request again after a `400` or `401`. After `NO_ACCESS` it gets a new access token first | Log of the refused call and what the supplier did next |
| SIT-07 | Handle refused POSTs | T-POST-12 to T-POST-16 | A `404` for a registration that does not exist, a `400` for a bad content type, id or missing request id, and a `401` for no access token are handled. The supplier does not retry them as if they were an outage | Log of each, and what the supplier did next |
| SIT-08 | Handle a rate limit | T-SUP-03 | After a `429` the supplier waits at least the `Retry-After` time, then sends the request again with the same `X-Request-ID`, and it succeeds | Log showing the `429`, the wait and the successful retry |
| SIT-09 | Handle an outage | T-SUP-04 | After a `503` or a timeout the supplier retries with increasing waits and the same `X-Request-ID`. If the retries run out, the registration is left open and comes back at the next sign-in | Log showing the retries. Then the next sign-in returning the registration |

## Stage 3 cases, with real registrations (SIT-10 to SIT-19)

| ID | Case | Expected | Evidence | Needs |
| --- | --- | --- | --- | --- |
| SIT-10 | New patient, enrolled and registered | A clinician enrols a test patient in the Patient Enrolment Service. The patient signs in to the supplier's app with NHS login. `GET /registrations` returns one real registration for the patient. The supplier sends a Task with `registered` and gets `200` | Screenshot of the enrolment. GET response with the registration id. The Task request and response | Stage 3 entry criteria |
| SIT-11 | NHS App message and hand-off | The patient gets the NHS App message. The link opens the HealthStore page, and the page takes the patient into the DTx app. This works on iPhone and Android. A patient who signs in to the supplier's app without the message is registered all the same | Screenshots on each phone of the message, the HealthStore page and the DTx app | NHS Notify and NHS App integration environments, not our stubs. Phones with the NHS App and each DTx app |
| SIT-12 | Acknowledged registration is not returned again | After SIT-10, the patient's next sign-in returns an empty Bundle, and the supplier sends no Task | The second GET response, showing `total` 0 | Tasks recorded |
| SIT-13 | Patient already has an account with the supplier | The supplier links the existing account and sends `registered` with `already-registered`. The response is `200` | The Task request and response, and a note of the account that was linked | A patient with an existing supplier account |
| SIT-14 | Two registrations, one sign-in | A patient enrolled for two interventions gets two registrations in one GET. The supplier sends two Tasks, one per registration id, each `200`. It does not send one Task for both | GET response with two ids. Both Task requests and responses | One patient enrolled twice |
| SIT-15 | Already registered for one intervention, enrolled for a second | The GET returns only the registration that is still open. The supplier sends a Task for that one only | Both GET responses, and the one Task | One patient, one registration acknowledged first |
| SIT-16 | Registration rejected | The supplier sends `rejected` with a coded reason, for example `intervention-not-configured`. The response is `200`. The clinician can see the outcome | The Task request and response. Screenshot of what the clinician sees | See open points |
| SIT-17 | A supplier sees only its own registrations | A patient enrolled with two suppliers gets one registration from each supplier's own token. Each Bundle holds only that supplier's registration id. The supplier cannot acknowledge the other supplier's id and gets `404` | Each supplier's GET response. The `404` | One patient enrolled with two suppliers |
| SIT-18 | ID token checks | With ID token checks on, a real NHS login sandpit token works. An expired token (`TOKEN_EXPIRED`), a wrong audience (`WRONG_AUDIENCE`), proofing below P9 (`PROOFING_BELOW_P9`) and a token with no NHS number (`NHS_NUMBER_MISSING`) are refused with `401`. The supplier handles each without crashing the sign-in | Each request and response. A log of the sign-in that follows | ID token verification switched on in Int, DTX-2673 |
| SIT-19 | Timing | The Task is sent within 1 second of the registration being processed. The patient can see and use the therapeutic within 10 seconds of NHS login | Log with timestamps. A recorded sign-in | Thresholds signed off, as SCAL-NFR-04 and SCAL-NFR-06 are still to be agreed |

## Rerunning (Stage 4, and any failed case)

- Stage 2 cases need no reset. Their outcomes change nothing.
- After a real Task is saved, `POST /registrations/reset` deletes that supplier's enrolments and enrols the synthetic set again as real, unacknowledged enrolments. It restores the whole set, not one patient, and it does not send the NHS App message again. The reset only exists on the dev namespaces, which includes `supplier-int`.
- To replay SIT-11, the clinician enrols the patient again.
- Once ID token checks are on, Stage 2 reruns need real NHS login sandpit tokens, not the test ones.


