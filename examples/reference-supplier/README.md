# reference-supplier

**Reflects the v0.1 draft transport. A v1.0 reference implementation
accompanies conformance testing.**

This Spring Boot application implements the v0.1 supplier-side API: a token
endpoint and an inbound registration-request endpoint that received push
Tasks from HealthStore. v1.0 removed that API. The platform now makes two
outbound calls from inside the patient's NHS login session and hosts nothing;
see [`docs/registration-api/overview.md`](../../docs/registration-api/overview.md).

Nothing here is normative for v1.0. It is kept as a record of the v0.1
shape and is no longer built by CI. Its Gradle build generates from
`specification/supplier-api.yaml`, which v1.0 removed, so it does not build
on this branch; the last commit with the v0.1 spec is `b240f64`.
