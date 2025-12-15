<!--
SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors

SPDX-License-Identifier: CC0-1.0
-->

# Digg Wallet Local Development Environment

Docker compose scripts for starting Digg Wallet environment services locally.

---

## The Docker Compose Script

Before running the local environment, it is necessary to pull all images:

> docker compose pull (to ensure you are using the latest images)

It is also necessary to set the environment variable `HOST_IP` to the host's IP address inside a docker container.
This can be done by running the following shell command:

> source set-host.sh

**Note:** It is necessary to run this with `source` in order to retain the environment variable outside the context of the script.

After this, the local environment can be started by:

> docker compose up

**Note:** When running the `set-host` script and running behind a corporate proxy, you might need
to add that IP to docker's noProxy config. An example:

```shell
echo $HOST_IP
```

could yield `172.17.0.1`. In that case, you would edit your docker config and restart docker which would result in something like this:

```shell
cat ~/.docker/config.json 
```

```json
{
  "proxies": {
    "default": {
      "httpProxy": "your-regular-proxy",
      "httpsProxy": "your-regular-proxy",
      "noProxy": "your-regular-no-proxy,172.0.0.0/8"
    }
  }
}
```

---

## Services

* [Strumpsorteringscentralen](https://localhost/custom-verifier),
  our custom verifier
* [EUs reference implementation of a verifier](https://refimpl-verifier.wallet.local)
* [EUs reference implementation of a verifier backend](https://localhost/refimpl-verifier-backend),
  used by both above
* [EUs reference implementation of a PID issuer](https://localhost/pid-issuer)
* [Keycloak](https://localhost/idp),
  identity provider for the PID issuer
* [Wallet provider](https://localhost/wallet-provider),
  our service to issue and control the lifecycle of Wallet Unit of Attestations (WUA).
* [Wallet Client Gateway](https://localhost/wallet-client-gateway),
  our BFF for the wallet app(s).
* [Wallet Account](https://localhost/wallet-account),
  our service to manage user accounts.
* [Wallet Attribute Attestation](https://localhost/wallet-attribute-attestation),
  our service to manage user's attribute attestations
* [Traefik](http://localhost:8080),
  used for TLS

---

## Building Images

If you need to add a new application to the Docker compose script its image need to be published.

## Prerequisites

The following prerequisites are needed for running the scrips:

### Hosts File

* Edit your computer's hosts-file to contain mappings from `127.0.0.1`
  to local services at `*.wallet.local`

```text
#
# Host Database
#
127.0.0.1       localhost
255.255.255.255 broadcasthost
::1             localhost

# Digg Wallet Ecosystem
#
127.0.0.1       refimpl-verifier.wallet.local
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
