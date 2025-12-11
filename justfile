# SPDX-FileCopyrightText: 2025 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

# Quality checks and automation for Wallet Ecosystem
# Run 'just' to see available commands

devtools_repo := env("DEVBASE_JUSTKIT_REPO", "https://github.com/diggsweden/devbase-justkit")
devtools_dir := env("XDG_DATA_HOME", env("HOME") + "/.local/share") + "/devbase-justkit"
lint := devtools_dir + "/linters"
java_lint := devtools_dir + "/linters/java"
colors := devtools_dir + "/utils/colors.sh"

maven_opts := "--batch-mode --no-transfer-progress --errors -Dstyle.color=always"

# Color variables
CYAN_BOLD := "\\033[1;36m"
GREEN := "\\033[1;32m"
BLUE := "\\033[1;34m"
NC := "\\033[0m"

# ==================================================================================== #
# DEFAULT - Show available recipes
# ==================================================================================== #

# Display available recipes
default:
    @printf "{{CYAN_BOLD}} Wallet Ecosystem{{NC}}\n\n"
    @printf "Quick start: {{GREEN}}just install{{NC}} | {{BLUE}}just verify{{NC}}\n\n"
    @just --list --unsorted

# ==================================================================================== #
# SETUP - Development environment setup
# ==================================================================================== #

# ▪ Install devtools and tools
[group('setup')]
install: setup-devtools tools-install

# ▪ Setup devtools (clone or update)
[group('setup')]
setup-devtools:
    #!/usr/bin/env bash
    set -euo pipefail
    if [[ -d "{{devtools_dir}}" ]]; then
        "{{devtools_dir}}/scripts/setup.sh" "{{devtools_repo}}" "{{devtools_dir}}"
    else
        printf "Cloning devbase-justkit to %s...\n" "{{devtools_dir}}"
        mkdir -p "$(dirname "{{devtools_dir}}")"
        git clone --depth 1 "{{devtools_repo}}" "{{devtools_dir}}"
        git -C "{{devtools_dir}}" fetch --tags --quiet
        latest=$(git -C "{{devtools_dir}}" describe --tags --abbrev=0 origin/main 2>/dev/null || echo "")
        if [[ -n "$latest" ]]; then
            git -C "{{devtools_dir}}" fetch --depth 1 origin tag "$latest" --quiet
            git -C "{{devtools_dir}}" checkout "$latest" --quiet
        fi
        printf "Installed devbase-justkit %s\n" "${latest:-main}"
    fi

# Check required tools are installed
[group('setup')]
check-tools: _ensure-devtools
    @{{devtools_dir}}/scripts/check-tools.sh --check-devtools mise git just java mvn rumdl yamlfmt actionlint gitleaks shellcheck shfmt conform reuse

# Install tools via mise
[group('setup')]
tools-install: _ensure-devtools
    @mise install

# Install npm dependencies (for prettier XML)
[group('setup')]
npm-install:
    npm ci

# ==================================================================================== #
# VERIFY - Quality assurance
# ==================================================================================== #

# ▪ Run all checks (linters only - tests require docker-compose)
[group('verify')]
verify: _ensure-devtools check-tools
    @{{devtools_dir}}/scripts/verify.sh

# ==================================================================================== #
# LINT - Code quality checks
# ==================================================================================== #

# ▪ Run all linters with summary
[group('lint')]
lint-all: _ensure-devtools
    @{{devtools_dir}}/scripts/verify.sh

# Validate commit messages
[group('lint')]
lint-commits:
    @{{lint}}/commits.sh

# Scan for secrets
[group('lint')]
lint-secrets:
    @{{lint}}/secrets.sh

# Lint YAML files
[group('lint')]
lint-yaml:
    @{{lint}}/yaml.sh check

# Lint markdown files
[group('lint')]
lint-markdown:
    @{{lint}}/markdown.sh check

# Lint shell scripts
[group('lint')]
lint-shell:
    @{{lint}}/shell.sh

# Check shell formatting
[group('lint')]
lint-shell-fmt:
    @{{lint}}/shell-fmt.sh check

# Lint GitHub Actions
[group('lint')]
lint-actions:
    @{{lint}}/github-actions.sh

# Check license compliance
[group('lint')]
lint-license:
    @{{lint}}/license.sh

# Lint XML files
[group('lint')]
lint-xml:
    @{{lint}}/xml.sh

# Lint Java code (all: checkstyle, pmd, spotbugs)
[group('lint')]
lint-java:
    @{{java_lint}}/lint.sh

# Lint Java - checkstyle only
[group('lint')]
lint-java-checkstyle:
    @{{java_lint}}/checkstyle.sh

# Lint Java - pmd only
[group('lint')]
lint-java-pmd:
    @{{java_lint}}/pmd.sh

# Check Java formatting
[group('lint')]
lint-java-fmt:
    @{{java_lint}}/format.sh check

# ==================================================================================== #
# LINT-FIX - Auto-fix code issues
# ==================================================================================== #

# ▪ Fix all auto-fixable issues
[group('lint-fix')]
lint-fix: _ensure-devtools lint-yaml-fix lint-markdown-fix lint-shell-fmt-fix lint-java-fmt-fix
    #!/usr/bin/env bash
    source "{{colors}}"
    just_success "All auto-fixes completed"

# Fix YAML formatting
[group('lint-fix')]
lint-yaml-fix:
    @{{lint}}/yaml.sh fix

# Fix markdown formatting
[group('lint-fix')]
lint-markdown-fix:
    @{{lint}}/markdown.sh fix

# Fix shell formatting
[group('lint-fix')]
lint-shell-fmt-fix:
    @{{lint}}/shell-fmt.sh fix

# Fix Java formatting
[group('lint-fix')]
lint-java-fmt-fix:
    @{{java_lint}}/format.sh fix

# ==================================================================================== #
# DOCKER COMPOSE - Service orchestration
# ==================================================================================== #

# Start all ecosystem services
[group('docker')]
up:
    #!/usr/bin/env bash
    set -euo pipefail
    if [[ -z "${HOST_IP:-}" ]]; then
        echo "Setting HOST_IP..."
        source set-host.sh
    fi
    docker compose up -d

# Stop all ecosystem services
[group('docker')]
down:
    docker compose down

# Pull latest images
[group('docker')]
pull:
    docker compose pull

# View service logs (optionally specify service name)
[group('docker')]
logs service="":
    docker compose logs -f {{service}}

# Show service status
[group('docker')]
status:
    docker compose ps

# Restart a specific service
[group('docker')]
restart service:
    docker compose restart {{service}}

# ==================================================================================== #
# TEST - Integration tests (requires docker-compose services)
# ==================================================================================== #

# ▪ Run integration tests (requires services running)
[group('test')]
test:
    #!/usr/bin/env bash
    source "{{colors}}"
    just_header "Running" "mvn test"
    mvn {{maven_opts}} test
    just_success "Tests completed"

# Run Maven verify
[group('test')]
verify-maven:
    #!/usr/bin/env bash
    source "{{colors}}"
    just_header "Running" "mvn verify"
    mvn {{maven_opts}} verify
    just_success "Maven verify completed"

# ==================================================================================== #
# CERTS - Certificate management
# ==================================================================================== #

# Generate TLS certificates (requires mkcert)
[group('setup')]
certs:
    #!/usr/bin/env bash
    set -euo pipefail
    source set-host.sh
    mkdir -p config/traefik/certs
    mkcert --cert-file ./config/traefik/certs/wallet-cert.pem \
           --key-file ./config/traefik/certs/wallet-key.pem \
           "*.wallet.local" localhost 127.0.0.1 ::1

# ==================================================================================== #
# INTERNAL
# ==================================================================================== #

[private]
_ensure-devtools:
    @just setup-devtools
