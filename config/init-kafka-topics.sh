#!/bin/sh

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

# All topic names, the bootstrap server, and topic configuration are injected
# via environment variables.
#
# Required:
#   KAFKA_BOOTSTRAP_SERVERS              - comma-separated broker list
#   WALLET_STATE_TOPIC                   - compacted state store topic
#   REQUEST_TOPICS                       - comma-separated request topics
#   RESPONSE_TOPICS                      - comma-separated per-BFF-instance response topics
#
# Optional — wallet state topic:
#   WALLET_STATE_PARTITIONS              - (default: 10)
#   WALLET_STATE_REPLICATION_FACTOR      - (default: 3)
#   WALLET_STATE_MIN_INSYNC_REPLICAS     - (default: 2)
#   WALLET_STATE_RETENTION_MS            - (default: -1, infinite)
#
# Optional — request and response topics (shared transient profile):
#   REQUEST_TOPIC_PARTITIONS             - (default: 10)
#   RESPONSE_TOPIC_PARTITIONS            - (default: 1)
#   TRANSIENT_REPLICATION_FACTOR        - (default: 2)
#   TRANSIENT_MIN_INSYNC_REPLICAS       - (default: 1)
#   TRANSIENT_RETENTION_MS              - (default: 600000, 10 minutes)

: "${KAFKA_BOOTSTRAP_SERVERS:?KAFKA_BOOTSTRAP_SERVERS must be set}"
: "${WALLET_STATE_TOPIC:?WALLET_STATE_TOPIC must be set}"
: "${REQUEST_TOPICS:?REQUEST_TOPICS must be set}"
: "${RESPONSE_TOPICS:?RESPONSE_TOPICS must be set}"

WALLET_STATE_PARTITIONS="${WALLET_STATE_PARTITIONS:-10}"
WALLET_STATE_REPLICATION_FACTOR="${WALLET_STATE_REPLICATION_FACTOR:-3}"
WALLET_STATE_MIN_INSYNC_REPLICAS="${WALLET_STATE_MIN_INSYNC_REPLICAS:-2}"
WALLET_STATE_RETENTION_MS="${WALLET_STATE_RETENTION_MS:--1}"

REQUEST_TOPIC_PARTITIONS="${REQUEST_TOPIC_PARTITIONS:-10}"
RESPONSE_TOPIC_PARTITIONS="${RESPONSE_TOPIC_PARTITIONS:-1}"
TRANSIENT_REPLICATION_FACTOR="${TRANSIENT_REPLICATION_FACTOR:-2}"
TRANSIENT_MIN_INSYNC_REPLICAS="${TRANSIENT_MIN_INSYNC_REPLICAS:-1}"
TRANSIENT_RETENTION_MS="${TRANSIENT_RETENTION_MS:-600000}"

# Creates a topic with delete cleanup policy using the transient profile defaults.
create_transient_topic() {
  topic="$1"
  partitions="$2"
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
    --create --if-not-exists --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor "$TRANSIENT_REPLICATION_FACTOR" \
    --config min.insync.replicas="$TRANSIENT_MIN_INSYNC_REPLICAS" \
    --config retention.ms="$TRANSIENT_RETENTION_MS" \
    --config cleanup.policy=delete
}

echo 'Waiting for Kafka cluster...'
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" --list

# ── Wallet state topic (compacted, typically infinite retention) ───────────────

/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
  --create --if-not-exists --topic "$WALLET_STATE_TOPIC" \
  --partitions "$WALLET_STATE_PARTITIONS" \
  --replication-factor "$WALLET_STATE_REPLICATION_FACTOR" \
  --config min.insync.replicas="$WALLET_STATE_MIN_INSYNC_REPLICAS" \
  --config retention.ms="$WALLET_STATE_RETENTION_MS" \
  --config cleanup.policy=compact

# ── Request topics ────────────────────────────────────────────────────────────

echo "$REQUEST_TOPICS" | tr ',' '\n' | while read -r topic; do
  [ -z "$topic" ] && continue
  create_transient_topic "$topic" "$REQUEST_TOPIC_PARTITIONS"
done

# ── Per-instance response topics ──────────────────────────────────────────────

echo "$RESPONSE_TOPICS" | tr ',' '\n' | while read -r topic; do
  [ -z "$topic" ] && continue
  create_transient_topic "$topic" "$RESPONSE_TOPIC_PARTITIONS"
done

# ── Summary ───────────────────────────────────────────────────────────────────

echo 'Topics:'
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" --list
