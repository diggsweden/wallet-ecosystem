![Logo](https://raw.githubusercontent.com/swedenconnect/technical-framework/master/img/sweden-connect.png)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# DIGG Wallet PoC Local Development Environment

Docker compose scripts for starting DIGG Wallet PoC environment services locally.

---

## The Docker Compose Script

Before running the local environment, it is necessary to pull all images:

> docker compose pull (to ensure you are using the latest images)

Then copy either the file `.env-linux` or `.env-mac` to `.env` to apply the correct configuration for following commands.

It is also necessary to set the environment variable `HOST_IP` to the host's IP address inside a docker container.
This can be done by running the following shell command:

> source set-host.sh

**Note:** It is necessary to run this with `source` in order to retain the environment variable outside the context of the script.

After this, the local environment can be started by:

> docker compose up

---

## Key services
 * [Reference impl verifier backend](https://refimpl-verifier-backend.wallet.local)
 * [Reference impl verifier frontend](https://refimpl-verifier.wallet.local)
 * [Custom verifier](https://custom-verifier.wallet.local)
 * [Wallet provider](https://wallet-provider.wallet.local)
 * [Traefik](https://traefik.wallet.local)
---

## Building Images

If you need to add a new application to the Docker compose script its image need to be published.

## Prerequisites

The following prerequisites are needed for running the scrips:

### .env file setup

Copy a .env file that fits your machine.

### Hosts File

- Edit your computer's hosts-file to contain mappings from `127.0.0.1`
  to local services at `*.wallet.local`

```
#
# Host Database
#
127.0.0.1       localhost
255.255.255.255 broadcasthost
::1             localhost

# Digg Wallet Ecosystem
#
127.0.0.1       custom-verifier.wallet.local
127.0.0.1       refimpl-verifier.wallet.local
127.0.0.1       refimpl-verifier-backend.wallet.local
127.0.0.1       wallet-provider.wallet.local
127.0.0.1       traefik.wallet.local
```

### Certificate for TLS

### Install mkcert

#### Debian/Ubuntu

```sh
sudo apt install libnss3-tools mkcert
mkcert --install
```

#### macOS

```sh
brew install mkcert
brew install nss  # Required for Firefox
```

### Trust mkcert CA root cert

Install the local CA in the system trust store:

```sh
mkcert -install
```

Note: The local issuer CA cert can be found with `cat "$(mkcert -CAROOT)/rootCA.pem"`

### Create Self-Signed Dev Certificate

Generate a certificate and key pair:

```sh
source set-host.sh
mkdir -p config/traefik/certs
mkcert --cert-file ./config/traefik/certs/wallet-cert.pem --key-file ./config/traefik/certs/wallet-key.pem "*.wallet.local" localhost 127.0.0.1 ::1
```


### Access to GitHubs Docker Registry

All images that are used by the Docker Compose script are available from GitHub's Docker Registry
located at `ghcr.io`. In order to access this registry you need to logon before running the
docker compose commands.

It is recommended that you assign the following environment variables:

- `GITHUB_USER` - Your GitHub username.

- `GITHUB_ACCESS_TOKEN` - Your GitHub access token, see [Authenticating to the Container registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-to-the-container-registry).

You can then execute the following command to authenticate:

```
echo $GITHUB_ACCESS_TOKEN | docker login ghcr.io -u $GITHUB_USER --password-stdin
```

> Note: Your GitHub user also needs access to the relevant Sweden Connect-repositories.


## Services

### Verifier

For running and building the containers for the openid4vp parts we have added a profile in docker-compose.yaml, named "verifier".

URL:s

Strumpsorteringscentralen
- https://custom-verifier.wallet.local

EUs reference implementation of a verifier
- https://refimpl-verifier.wallet.local

EUs reference implementation of a verifier backend, used by both above
- https://refimpl-verifier-backend.wallet.local

Traefik
Used for TLS
- http://localhost:8080
