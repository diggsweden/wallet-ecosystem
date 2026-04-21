# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government

##

## SPDX-License-Identifier: CC0-1.0

## Access Mechanism PoC

Start the stack:

```sh
just setup

podman compose up -d
```

## just setup

just setup runs these steps:

1. `just clone` clones <https://github.com/diggsweden/wallet-r2ps>
2. `just copy-env` copy env files wallet-r2ps
3. `just copy-env` copy env file from wallet-ecosystem () parent dir
4. `just copy-env` copy config needed from wallet-r2ps/config
5. `just build` build container images (rust-r2ps-worker, r2ps-rest-api)
6. `just clean-repo` remove repo from local

The `r2ps-rest-api` is available at `http://localhost:8088/r2ps-api`.

## Potential pitfalls

The stack uses Traefik, which needs the Podman socket:

```sh
systemctl --user enable --now podman.socket
```

Podman requires fully qualified image names. Add `docker.io` as a fallback:

```sh
mkdir -p ~/.config/containers
echo 'unqualified-search-registries = ["docker.io"]' > ~/.config/containers/registries.conf
```

### Privileged ports (80/443)

Rootless Podman cannot bind to ports below 1024 by default. Temporary fix:

```sh
sudo sysctl -w net.ipv4.ip_unprivileged_port_start=80
```

Add a persistent sysctl override:

```sh
echo 'net.ipv4.ip_unprivileged_port_start=80' | sudo tee /etc/sysctl.d/99-rootless-podman.conf
sudo sysctl -p /etc/sysctl.d/99-rootless-podman.conf
```

Not tested with docker but shouldn't be any difference :fingers_crossed
