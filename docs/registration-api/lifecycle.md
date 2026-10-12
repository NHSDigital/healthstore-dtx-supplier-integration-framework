# Lifecycle

Stub. The post-registration lifecycle follows the v1.0 baseline as a
versioned change, per the agreement in the review of PR #7. v1.0 covers
registration only: `businessStatus` `registered` and `rejected` on
`POST /registrations/{registration-id}/tasks`.

## In scope for the lifecycle specification

- **Activation.** Whether it remains distinct from registration. In v1.0
  `registered` already proves the patient has signed in, so activation only
  means something for products with post-sign-in onboarding. For the rest it
  collapses into registration.
- **Deactivation**, with coded closure reasons. The reasons include the
  registered-never-activated case, where a patient is registered but never
  reaches the point of use and is then removed by the healthcare
  professional. A full list of deactivation scenarios is owed by the
  platforms, including whether any is time-based.
- **Off-boarding.** Reported on the same path with a `deactivated`
  `businessStatus` and a reason. There is no separate off-boarding operation.
- **Retraction.** How a clinician withdraws a request, including where the
  clinician has no route to do so in the product. Patient-initiated
  withdrawal is not a concept for every platform; where it exists it is
  clinician-led.
- **Natural completion** of a course, and whether it is the same signal as
  deactivation or a distinct one.

## Carried forward from v0.1

- De-registration means the patient is no longer able to access the digital
  therapeutic. It is not an instruction to delete. What the platform does
  with what it already holds, clinical records in particular, is its own.
- De-registration is irreversible. Putting the same patient back onto the
  same product is a new registration with a new registration identifier.
- The platform reports; it does not alter the `ServiceRequest`.

## Mechanism

The lifecycle specification extends the `BusinessStatus` CodeSystem and adds
a closure-reason CodeSystem on the same Task shape and path, so a platform's
v1.0 acknowledgement client is reused unchanged.
