# Health Store Supplier Integration Framework

Contract repository for DTx-supplier integration with the Health Store:
specs, schemas, examples and tests.

## Registration API

- [Overview](docs/registration-api/overview.md)
- [FHIR resources and fields](docs/registration-api/fhir.md)
- [Resources and paths](docs/registration-api/resources.md)

## Examples

- [reference-supplier](examples/reference-supplier): a supplier platform
  with server and client code generated from the specification.

## Validating the specs

The OpenAPI documents under `specification/` and `schemas/` are linted with
[Spectral](https://github.com/stoplightio/spectral), configured in
`.spectral.yaml`.

```sh
npm install
npm run lint:spec
```

## How to contribute

- NHS Digital: raise an issue or open a PR; see `.github/PULL_REQUEST_TEMPLATE.md`.
- Collaborators: fork the repository and open a pull request.
- Review ownership: `.github/CODEOWNERS`.
- Security disclosure: `.github/SECURITY.md`.

## Licence

MIT; see `LICENCE.md`. HTML/Markdown documentation is © Crown Copyright
and available under the Open Government Licence v3.0.
