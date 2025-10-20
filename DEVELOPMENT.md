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
