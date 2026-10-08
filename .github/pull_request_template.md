## Summary

<!-- What does this PR do and why? Link the requirement/handoff docs if any: docs/delivery/<requirement-slug>/ -->

## Modules affected

-

## Type of change

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor (no behavior change)
- [ ] Infrastructure / Compose / Keycloak config
- [ ] Documentation only
- [ ] Breaking change (requires migration notes below)

## Changes

<!-- Bullet list of the key changes. if a spec file is used to generate the code for this pr reference it here using the spec id-->

-

## Testing

- [ ] `./mvnw -B verify` (or targeted `-pl :artifactId -am verify`) — passed
- [ ] `npm run test` (dashboard) — passed
- [ ] Playwright scenarios — passed
- [ ] 100% JaCoCo line coverage for handwritten production code in scope — measured and enforced
- [ ] Sonar scan - passed
- [ ] Manual verification steps (describe if applicable):

## Security / auth impact

- [ ] No change to authN/authZ, tokens, roles, or Keycloak config
- [ ] Changed — 401/403/role/tenant boundaries covered by tests
- [ ] No secrets, tokens, or client secrets introduced in code, config, or frontend bundle
- [ ] TBD DAST tool action - passed

## Compatibility & contracts

- [ ] Backwards compatible, or breaking change explicitly agreed and documented
- [ ] Generated code (OpenAPI clients/interfaces) not manually edited — generator source updated instead
- [ ] Contract, implementation, and generated clients kept in sync

## Docs & handoff

- [ ] Handoff/decision docs updated under `docs/delivery/<requirement-slug>/` if this followed the fixed workflow
- [ ] README / module docs updated if behavior, setup, or ports changed

## Risks & open items

<!-- Explicitly state any open risks, manual steps, or checks not performed -->

-
