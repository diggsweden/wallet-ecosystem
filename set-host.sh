#!/bin/bash

# SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
#
# SPDX-License-Identifier: EUPL-1.2

# Resolve the IP address of host.docker.internal and store it in HOST_IP

export SET_HOST_EXTRA_PARAM='--add-host=host.docker.internal:host-gateway'
HOST_IP=$(docker run --rm $SET_HOST_EXTRA_PARAM alpine sh -c 'ping -c 1 host.docker.internal | grep PING | awk "{ print \$3 }" | tr -d "(): "')
export HOST_IP

# Output the HOST_IP for debugging (optional)
echo "Resolved HOST_IP: $HOST_IP"
