# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

# Copy of eudi-srv-web-issuing-frontend-eudiw-py/Dockerfile without
# "COPY postcss.config.* ./": the repo has no such file and buildah, unlike
# docker, fails on a COPY glob that matches nothing.
# ── Stage 1: Node build ───────────────────────────────────────────────────────
FROM node:lts-slim AS node-builder

WORKDIR /build

COPY package*.json ./
COPY tailwind.config.* ./
COPY assets/ ./assets/
COPY app/ ./app/

RUN npm install
RUN npm run build

# ── Stage 2: Python deps ──────────────────────────────────────────────────────
FROM python:3.13-slim AS python-builder

WORKDIR /build

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    gcc \
    libffi-dev \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

COPY app/requirements.txt .
RUN pip install --upgrade pip \
 && pip install --no-cache-dir --prefix=/install -r requirements.txt

# ── Stage 3: runtime ──────────────────────────────────────────────────────────
FROM python:3.13-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libffi8 \
    libssl3 \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=python-builder /install /usr/local
COPY --from=node-builder /build/app/static/css/tailwind.css ./app/static/css/tailwind.css
COPY . .

ENV FLASK_APP=app


RUN useradd --system --uid 10001 issuer \
 && chown -R issuer /app /tmp
USER issuer

EXPOSE 5000

CMD ["flask", "run", "--host=0.0.0.0"]
