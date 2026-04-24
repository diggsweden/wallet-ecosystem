# Development Guide

This guide outlines core essentials for developing in this project.

## Table of Contents

- [Setup and Configuration](#setup-and-configuration)
  - [Prerequisites](#prerequisites---linux)
  - [Prerequisites](#prerequisites---macos)
  - [IDE Setup](#ide-setup)
- [Development Workflow](#development-workflow)
  - [Docker Compose Services](#docker-compose-services)
  - [Automated Tests](#automated-tests)
  - [Quality Checks](#quality-checks)
  - [Pull Request Workflow](#pull-request-workflow)

## Setup and Configuration

### Prerequisites - Linux

1. Install [mise](https://mise.jdx.dev/) (manages linting tools):

   ```bash
   curl https://mise.run | sh
   ```

2. Activate mise in your shell:

   ```bash
   # For bash - add to ~/.bashrc
   eval "$(mise activate bash)"

   # For zsh - add to ~/.zshrc
   eval "$(mise activate zsh)"

   # For fish - add to ~/.config/fish/config.fish
   mise activate fish | source
   ```

   Then restart your terminal.

3. Install pipx (needed for reuse license linting):

   ```bash
   # Debian/Ubuntu
   sudo apt install pipx
   ```

4. Install project tools:

   ```bash
   mise install
   just install
   ```

   This will clone devbase-check and install all required tools via mise.

### Prerequisites - macOS

1. Install [mise](https://mise.jdx.dev/) (manages linting tools):

   ```bash
   brew install mise
   ```

2. Activate mise in your shell:

   ```bash
   # For zsh - add to ~/.zshrc
   eval "$(mise activate zsh)"

   # For bash - add to ~/.bashrc
   eval "$(mise activate bash)"

   # For fish - add to ~/.config/fish/config.fish
   mise activate fish | source
   ```

   Then restart your terminal.

3. Install newer bash than macOS default:

   ```bash
   brew install bash
   ```

4. Install pipx (needed for reuse license linting):

   ```bash
   brew install pipx
   ```

5. Install project tools:

   ```bash
   mise install
   just install
   ```

   This will clone devbase-check and install all required tools via mise.

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

All available commands:

```shell
just --list
or
just
```

### Documentation

Generate documentation to `target/documentation`.

```shell
just document
```

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

### Generating a tests report

After you have the tests you can generate a nice HTML report:

```shell
just produce-test-report
open target/surefire-reports/open-test-report.html
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

When your host is configured with a specific API key you can configure the test
suite like so:

```shell
env DIGG_WALLET_ECOSYSTEM_WALLET_CLIENT_GATEWAY_BASE_URI=https://api.example.com \
    DIGG_WALLET_ECOSYSTEM_WALLET_CLIENT_GATEWAY_API_KEY=my_api_key \
    just test
```

#### Skipping Keycloak health tests

Under some configurations the Keycloak health endpoints are not exposed.
In order to avoid test failures in those situations, you can skip those tests like so:

```shell
env DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_KEYCLOAK_HEALTH=true just test
```

Similarly, you can skip the verifier backend tests like so:

```shell
env DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_VERIFIER_BACKEND_HEALTH=true just test
```

#### Excluding entire test cases

To exclude one or more test cases you can run the tests with Maven like so:

```shell
mvn test -Dtest.excludes='TraefikTest,WalletAccountTest,WalletAttributeAttestationTest'
```

The command above will run all test cases except
TraefikTest, WalletAccountTest and WalletAttributeAttestationTest.

#### Checking that the verifier rejects untrusted issuers

In `VerifierBackendTest` we have a way to check that
the local verifier rejects credentials issued by an untrusted party.
Specifically, that the verifier reject credentials issued by
a party not in the configured list of trust sources. There are two tests related
to this:

1. `rejectsUntrustedPidIssuer`
2. `rejectsCredentialFromUntrustedSource`

The first one requires a live, but untrusted, environment
and the second one uses pre-generated credentials known to be untrusted.
Since the first test requires a live environment it is disabled by default.

You can run it like so:

```shell
env \
	DIGG_WALLET_ECOSYSTEM_INCLUDE_TESTS_WITH_UNTRUSTED_ISSUER=true \
	DIGG_WALLET_ECOSYSTEM_UNTRUSTED_WALLET_PROVIDER_BASE_URI=https://example.com/untrusted-wallet-provider \
	DIGG_WALLET_ECOSYSTEM_UNTRUSTED_PID_ISSUER_BASE_URI=https://example.com/untrusted-pid-issuer \
	DIGG_WALLET_ECOSYSTEM_UNTRUSTED_KEYCLOAK_BASE_URI=https://example.com/untrusted-idp \
	mvn test -Dtest=VerifierBackendTest#rejectsUntrustedPidIssuer
```

The live test can be used to generate data for the second test.
In order to do so, we can manipulate the certificates
so that they are not trusted by the verifier.

```shell
# Remove the root CA so that a new one is generated
rm config/certificates/rootca/*

# Generate new keystores for all services
config/certificates/generate_keystores.sh

# Use the existing trusted issuers instead of the newly generated ones
git checkout config/certificates/verifier/trusted_issuers.p12

# Restart environment
docker compose restart

# Run test
env \
	DIGG_WALLET_ECOSYSTEM_INCLUDE_TESTS_WITH_UNTRUSTED_ISSUER=true \
	DIGG_WALLET_ECOSYSTEM_UNTRUSTED_WALLET_PROVIDER_BASE_URI=https://localhost/wallet-provider \
	DIGG_WALLET_ECOSYSTEM_UNTRUSTED_PID_ISSUER_BASE_URI=https://localhost/pid-issuer \
	DIGG_WALLET_ECOSYSTEM_UNTRUSTED_KEYCLOAK_BASE_URI=https://localhost/idp \
	mvn test -Dtest=VerifierBackendTest#rejectsUntrustedPidIssuer
```

The test will print the credentials and signing key to standard out
and this data can be copied into the corresponding variable values
of the `rejectsCredentialFromUntrustedSource` test method.

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
