<!--
SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors

SPDX-License-Identifier: CC0-1.0
-->

# Development Guide

This guide outlines core essentials for developing in this project.

## Table of Contents

- [Setup and Configuration](#setup-and-configuration)
  - [IDE Setup](#ide-setup)
- [Development Workflow](#development-workflow)
  - [Pull Request Process](#pull-request-workflow)

## Setup and Configuration

### IDE Setup

[Run the code quality script](#running-code-quality-checks-locally).

```shell
./development/code_quality.sh
```

This will run the automated test suite
and other automation such as linters and formatters.
As a side effect all required dependencies will be downloaded.
After running the script, please take a look in
[the generated IDE configuration file](./megalinter-reports/IDE-config.txt).
It contains a list of suggested plugins and configuration for various editors and IDEs,
e.g. VS Code and IntelliJ.

#### VSCode

 1. Install plugins:

    - [markdownlint](https://marketplace.visualstudio.com/items?itemName=DavidAnson.vscode-markdownlint)
    - [Prettier](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode)
    - [ShellCheck](https://marketplace.visualstudio.com/items?itemName=timonwong.shellcheck)
    - [shell-format](https://marketplace.visualstudio.com/items?itemName=foxundermoon.shell-format) version 7.2.5

        **Note 1:** There is
        [a known issue](https://github.com/foxundermoon/vs-shell-format/issues/396)
        with version 7.2.8 of shell-format
        preventing it from being detected as a formatter for shell scripts.
        Please use version 7.2.5 until the issue is fixed.

        **Note 2:** You need to have the `shfmt` binary installed in order to use the plugin.
        On Ubuntu you can install it with `sudo apt-get install shfmt`.

 2. Open workspace settings - settings.json (for example with Ctrl+Shift+P → Preferences: Workspace Settings (JSON)) and add:

    ```json
    "editor.formatOnSave": true,
    "shellformat.path": "<path to shfmt>",
    "[markdown]": {
        "editor.defaultFormatter": "DavidAnson.vscode-markdownlint"
    },
    ```

## Development Workflow

### Automated tests

We have [an automated test suite](./src/test/java/) for the Wallet ecosystem.
The main goals of this suite are:

 1. To document the interaction between the different services,
 2. To verify that those services can communicate with each other, and
 3. To simplify manual exploration and learning.

At the current stage the test suite is just a skeleton.
However, we except to grow the suite over time.

In order to run the test suite from the command line,
use the commands below:

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
env DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_USING_CUSTOM_HOSTS=true mvn test
```

#### Running tests on alternate hosts

Normally the tests suite runs against the wallet ecosystem deployed locally.
In order to run the tests on alternate hosts,
you can configure the location of those hosts using environment variables like so:

```shell
env DIGG_WALLET_ECOSYSTEM_WALLET_PROVIDER_BASE_URI=https://wallet-provider.example.com \
    DIGG_WALLET_ECOSYSTEM_PID_ISSUER_BASE_URI=https://pid-issuer.example.com \
    DIGG_WALLET_ECOSYSTEM_KEYCLOAK_BASE_URI=https://keycloak.example.com \
    mvn test
```

#### Skipping Keycloak health tests

Under some configurations the Keycloak health endpoints are not exposed.
In order to avoid test failures in those situations, you can skip those tests like so:

```shell
env DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_KEYCLOAK_HEALTH=true mvn test
```

### Pull Request Workflow

#### Pull Request Workflow Prerequisites

In order to run the code quality script,
you need a recent version of
[the Bash shell](https://www.gnu.org/software/bash/)
(version 4.0 or newer) with support for associative arrays.
As of this writing, the default version on macOS is version 3.2,
which is too old.
You can install a later version using [Homebrew](https://brew.sh/) like so:

```shell
brew install bash
```

This should get you version 5.3 or later.

You can check your bash version like this:

```shell
bash --version
```

#### Running Code Quality Checks Locally

1. Run the quality check script:

   ```shell
   ./development/code_quality.sh
   ```

   **Note:**
   If you are behind a corporate proxy you might experience problems
   where the script hangs for several minutes
   while it tries to download files from the internet
   and eventually times out.
   To avoid this problem you can set the enviroment variable
   `MEGALINTER_SKIP_LINTER_OUTPUT_SANITIZATION=true`.
   For instance, you could configure the variable in your global settings,
   or run the script like so:

   ```shell
   env MEGALINTER_SKIP_LINTER_OUTPUT_SANITIZATION=true development/code_quality.sh
   ```

2. Fix any identified issues

3. Update your PR with fixes

4. Verify CI passes in the updated PR
