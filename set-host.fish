#!/usr/bin/fish

# SPDX-FileCopyrightText: 2025 Digg Sweden
#
# SPDX-License-Identifier: EUPL-1.2

# Resolve the IP address of host.docker.internal and store it in HOST_IP
set -Ux SET_HOST_EXTRA_PARAM --add-host=host.docker.internal:host-gateway
set -Ux HOST_IP $(docker run --rm $SET_HOST_EXTRA_PARAM alpine sh -c 'ping -c 1 host.docker.internal | grep PING | awk "{ print \$3 }" | tr -d "(): "')

# Output the HOST_IP for debugging (optional)
echo "Resolved HOST_IP: $HOST_IP"
