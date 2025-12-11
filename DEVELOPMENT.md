<!--
SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors

SPDX-License-Identifier: CC0-1.0
-->

# Development Guide

This guide outlines core essentials for developing in this project.

## Table of Contents

- [Setup and Configuration](#setup-and-configuration)
  - [Prerequisites](#prerequisites)
  - [IDE Setup](#ide-setup)
- [Development Workflow](#development-workflow)
  - [Docker Compose Services](#docker-compose-services)
  - [Automated Tests](#automated-tests)
  - [Quality Checks](#quality-checks)
  - [Pull Request Workflow](#pull-request-workflow)

## Setup and Configuration

### Prerequisites

[mise](https://mise.jdx.dev/) for tool management, then install tools:

```shell
mise install
just install
```

This will clone devbase-justkit and install all required tools via mise.

### IDE Setup

Run the quality checks to verify your setup:

```shell
just verify
```

#### VSCode

1. Install plugins:

   - [markdownlint](https://marketplace.visualstudio.com/items?itemName=DavidAnson.vscode-markdownlint)
   - [ShellCheck](https://marketplace.visualstudio.com/items?itemName=timonwong.shellcheck)
   - [shell-format](https://marketplace.visualstudio.com/items?itemName=foxundermoon.shell-format) version 7.2.5

2. Open workspace settings - settings.json (for example with Ctrl+Shift+P → Preferences: Workspace Settings (JSON)) and add:

   ```json
   "editor.formatOnSave": true,
   "shellformat.path": "<path to shfmt>",
   "[markdown]": {
       "editor.defaultFormatter": "DavidAnson.vscode-markdownlint"
   },
   ```

## Development Workflow

### Docker Compose Services

Start all ecosystem services:

```shell
just up
```

Other docker compose commands:

```shell
just down      # Stop all services
just pull      # Pull latest images
just logs      # View all logs
just logs keycloak  # View specific service logs
just status    # Show service status
```

### Automated Tests

We have [an automated test suite](./src/test/java/) for the Wallet ecosystem.
The main goals of this suite are:

1. To document the interaction between the different services,
2. To verify that those services can communicate with each other, and
3. To simplify manual exploration and learning.

At the current stage the test suite is just a skeleton.
However, we expect to grow the suite over time.

#### Running Tests

First start the services, then run tests:

```shell
just up
just test
```

Or using docker-compose and Maven directly:

```shell
docker-compose up -d
mvn test
```

#### Skipping tests using custom hosts

Some of our services uses a custom host mapping,
e.g. `refimpl-verifier.wallet.local`.
On machines where the user cannot change the hosts mapping
the automated tests that try to use a service using such a host name will fail.
To workaround this problem,
you can skip those tests by setting an environment variable
and run the test suite like so:

```shell
env DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_USING_CUSTOM_HOSTS=true just test
```

#### Running tests on alternate hosts

Normally the tests suite runs against the wallet ecosystem deployed locally.
In order to run the tests on alternate hosts,
you can configure the location of those hosts using environment variables like so:

```shell
env DIGG_WALLET_ECOSYSTEM_WALLET_PROVIDER_BASE_URI=https://wallet-provider.example.com \
    DIGG_WALLET_ECOSYSTEM_PID_ISSUER_BASE_URI=https://pid-issuer.example.com \
    DIGG_WALLET_ECOSYSTEM_KEYCLOAK_BASE_URI=https://keycloak.example.com \
    just test
```

#### Skipping Keycloak health tests

Under some configurations the Keycloak health endpoints are not exposed.
In order to avoid test failures in those situations, you can skip those tests like so:

```shell
env DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_KEYCLOAK_HEALTH=true just test
```

### Quality Checks

This project uses `just` + `mise` for local quality checks.
The same checks run in CI on pull requests.

#### Running Quality Checks

Run quality checks before submitting a PR:

```shell
just verify
```

This runs all linters. To run specific checks:

```shell
just lint-all         # All linters with summary
just lint-commits     # Commit message validation
just lint-secrets     # Secret scanning
just lint-yaml        # YAML linting
just lint-markdown    # Markdown linting
just lint-shell       # Shell script linting
just lint-actions     # GitHub Actions linting
just lint-license     # REUSE license compliance
just lint-java        # Java linting (checkstyle, pmd)
just lint-java-fmt    # Java formatting check
```

To auto-fix issues:

```shell
just lint-fix         # Fix all auto-fixable issues
just lint-yaml-fix    # Fix YAML formatting
just lint-markdown-fix  # Fix markdown formatting
just lint-java-fmt-fix  # Fix Java formatting
```

All available commands:

```shell
just --list
```

### Pull Request Workflow

1. Make your changes

2. Run quality checks locally:

   ```shell
   just verify
   ```

3. Fix any identified issues (or use `just lint-fix` for auto-fixable issues)

4. Commit with a conventional commit message (e.g., `feat: add new feature`)

5. Push and create PR

6. Verify CI passes in the updated PR

**Note:** Integration tests are NOT run in CI since they require the full
docker-compose stack. Run tests locally before submitting PRs that affect
service integration.
