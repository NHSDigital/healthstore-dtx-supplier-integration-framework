# healthstore-simulator

An in-memory stand-in for NHS HealthStore, implementing both sides of the
registration contract in `../../specification`. It sends registration request
Tasks to a supplier platform, and serves the Registrations API that the
platform retrieves from and reports lifecycle Tasks to.

State is held in memory. A restart clears it.

## Running

```
mise install
./gradlew bootRun
```

The simulator listens on port 8090. Registration requests are sent to
`simulator.supplier-base-url`, default `http://localhost:8080`, where the
reference supplier runs.

## Contract surface

As specified in `../../specification/healthstore-api.yaml`:

| Method | Path |
|---|---|
| GET | `/registrations/{registration-id}` |
| GET | `/registrations?cohort={id}&_count={n}&page={p}` |
| POST | `/registrations/{registration-id}/tasks` |

## Control plane

Endpoints under `/_simulator` stand in for the upstream that creates
registrations in the real service, such as enrolment and cohort closure. They
are not part of the contract.

| Method | Path | |
|---|---|---|
| GET | `/_simulator/fixtures` | List patient fixtures |
| POST | `/_simulator/registrations?fixture=&priority=&cohort=` | Create one registration; all parameters optional |
| POST | `/_simulator/cohorts/{cohort}/registrations?count=n` | Create a cohort of n registrations, cycling through fixtures |
| POST | `/_simulator/registrations/{id}/send` | Send the registration request for one registration |
| POST | `/_simulator/cohorts/{cohort}/send` | Send the registration request for a cohort |
| POST | `/_simulator/registrations/{id}/status/{status}` | Set a registration's status, for example `revoked` |
| GET | `/_simulator/state` | Statuses, worklist membership, received Tasks |
| POST | `/_simulator/reset` | Clear all state |

A repeated send reuses the stored Task and `X-Request-ID`, so a redelivery is
identical to the first attempt.

## One patient

```
curl -X POST 'http://localhost:8090/_simulator/registrations?priority=asap'
curl -X POST 'http://localhost:8090/_simulator/registrations/{id}/send'
curl 'http://localhost:8090/registrations/{id}' -H 'X-Request-ID: <uuid>'
curl -X POST 'http://localhost:8090/registrations/{id}/tasks' \
  -H 'Content-Type: application/fhir+json' -H 'X-Request-ID: <uuid>' \
  -d '{"resourceType":"Task","status":"accepted","intent":"order","businessStatus":{"text":"registered"}}'
curl 'http://localhost:8090/_simulator/state'
```

The lifecycle Task removes the registration from the worklist.

## A cohort

```
curl -X POST 'http://localhost:8090/_simulator/cohorts/my-cohort/registrations?count=5'
curl -X POST 'http://localhost:8090/_simulator/cohorts/my-cohort/send'
curl 'http://localhost:8090/registrations?cohort=my-cohort&_count=2&page=1' -H 'X-Request-ID: <uuid>'
```

The worklist shrinks as lifecycle Tasks are recorded and is empty once every
registration has been actioned.

## Fixtures

`src/main/resources/fixtures/*.json` hold one registration payload per
patient, in the contract's wire format, with PDS test NHS numbers. Seeding
clones a fixture, assigns a new registration identifier and sets
`authoredOn`; `priority` overrides the fixture's value when given. New
patients are added as new files.

## Configuration

| Property | Default | |
|---|---|---|
| `server.port` | `8090` | Both surfaces |
| `simulator.supplier-base-url` | `http://localhost:8080` | Where registration requests are sent |
| `simulator.public-base-url` | `http://localhost:8090` | Base of the Bundle paging links |
| `simulator.fixed-time` | unset | ISO date time; fixes the clock |

## Not simulated

Authentication, rate limiting and retry scheduling are open questions in the
contract, so the simulator does not implement them. OperationOutcome error
responses for malformed requests and for unknown registrations and cohorts
are implemented.
