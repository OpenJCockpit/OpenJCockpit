# Contributing to OpenJCockpit

Thanks for your interest in improving OpenJCockpit.

This project is published under the Apache License 2.0 and welcomes high-quality bug reports, fixes, documentation improvements, and examples.

## Ways to contribute

- Report bugs or unexpected behavior
- Suggest enhancements, agents or workflows
- Improve documentation and examples
- Submit code changes and tests

## Before you start

- Check whether an issue already exists for the topic you want to work on.
- Keep changes focused and small enough to review easily.
- Prefer clear commit messages and tests for behavioral changes.

## Development setup

Requirements:

- JDK 25
- Maven 3.9+
- Node.js 22 and npm (see `frontend/.nvmrc`)
- Docker with Docker Compose, for running the full stack
- A local Ollama installation, as described in [LLM providers](docs/readme/integrations/llm-providers.md)

Common commands:

```bash
# Backend services
mvn -B verify
mvn -B -pl :ai-control-service -am verify

# Frontend (Nx workspace; run from the repo root)
cd frontend
npm ci
npx nx affected -t lint typecheck test   # or `npm run check` for the full quick gate

# Full stack
docker compose up --build
```

See the [Quickstart](docs/readme/getting-started/quickstart.md) for the full stack setup, [Local development](docs/readme/development/local-development.md) for building and verifying from source, and [Testing](docs/readme/development/testing.md) for the end-to-end tests (Playwright).

## Coding guidelines

- Prefer clear, idiomatic Java and TypeScript code that matches the surrounding code.
- Keep public APIs and REST contracts minimal and documented.
- Do not edit generated code (such as OpenAPI clients) by hand; update the generator source instead.
- Add or update tests for logic changes and bug fixes.
- Never commit secrets, tokens or client secrets in code, configuration or the frontend bundle.
- Avoid introducing unrelated refactors in the same change.

## Pull requests

1. Fork or branch from the repository's default branch.
2. Make your change with clear, focused commits.
3. Run the relevant tests for the affected modules.
4. Open a pull request using the [pull request template](.github/pull_request_template.md) with:
   - a concise title
   - a summary of the change
   - related issue references when applicable
   - validation steps performed

## Code of conduct

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md). We aim to maintain a respectful, welcoming, and productive contributor community.

## License

By contributing, you agree that your contributions will be licensed under the project's [LICENSE](LICENSE).
