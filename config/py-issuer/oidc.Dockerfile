# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

# eudi-srv-issuer-oidc-py's own Dockerfile plus a fix for the idpy-oidc fork:
# since 3f3a5bd ("TS3 v1.5") the trusted-attester success log references the
# removed `_iss`, so every matching WIA fails with NameError and is reported as
# "no trusted attester matched".
FROM python:3.13-slim AS builder

WORKDIR /build

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    gcc \
    libffi-dev \
    libssl-dev \
    ca-certificates \
    git \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --upgrade pip \
 && pip install --no-cache-dir --prefix=/install -r requirements.txt

FROM python:3.13-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libffi8 \
    libssl3 \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /install /usr/local
RUN sed -i "s/(cert {idx}): {_iss}\"/(cert {idx}): {_wia.get('iss')}\"/" \
    /usr/local/lib/python3.13/site-packages/idpyoidc/server/client_authn.py \
 && grep -q "_wia.get('iss')}" /usr/local/lib/python3.13/site-packages/idpyoidc/server/client_authn.py
COPY . .

RUN chmod +x run.sh \
 && mkdir -p /tmp/oidc_log_dev


RUN useradd --system --uid 10001 issuer \
 && chown -R issuer /app /tmp
USER issuer

EXPOSE 5000

CMD ["python3", "server.py", "/etc/issuer_config/authorization_config.json"]
