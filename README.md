# Health Store Supplier Integration Framework

Contract repository for DTx-supplier integration with the Health Store:
specs, schemas, examples and tests.

## Registration API

- [Overview](docs/registration-api/overview.md)
- [FHIR resources and fields](docs/registration-api/fhir.md)
- [Resources and paths](docs/registration-api/resources.md)

## Examples

Runnable examples of both sides of the registration contract:

- [reference-supplier](examples/reference-supplier): a supplier platform
  with server and client code generated from the specification.
- [healthstore-simulator](examples/healthstore-simulator): an in-memory
  Health Store stand-in that sends registration requests and serves the
  Registrations API; see its [README](examples/healthstore-simulator/README.md).

## How to contribute

- NHS Digital: raise an issue or open a PR; see `.github/PULL_REQUEST_TEMPLATE.md`.
- Collaborators: fork the repository and open a pull request.
- Review ownership: `.github/CODEOWNERS`.
- Security disclosure: `.github/SECURITY.md`.

## Licence

MIT; see `LICENCE.md`. HTML/Markdown documentation is © Crown Copyright
and available under the Open Government Licence v3.0.
