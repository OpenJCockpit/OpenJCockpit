#!/usr/bin/env bash
# Shared Docker HEALTHCHECK of the OpenJCockpit Java service images: passes when the readiness endpoint
# reports UP. Usage: healthcheck.sh <default management port>; MANAGEMENT_PORT overrides the port.
# Tries plain HTTP first and falls back to TLS, since the management endpoints share the application port
# (HTTPS when a certificate is mounted, see entrypoint.sh) unless a separate MANAGEMENT_PORT is set.
port="${MANAGEMENT_PORT:-$1}"
request=$'GET /actuator/health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n'

(exec 3<>"/dev/tcp/127.0.0.1/$port" && printf '%s' "$request" >&3 && grep -q '"status":"UP"' <&3) && exit 0

printf '%s' "$request" \
  | timeout 4 openssl s_client -quiet -connect "127.0.0.1:$port" -servername localhost 2>/dev/null \
  | grep -q '"status":"UP"'
