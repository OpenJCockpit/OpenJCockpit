#!/usr/bin/env bash
# Shared entrypoint of the OpenJCockpit Java service images.
#
# Serves HTTPS when a PEM certificate and private key are mounted, plain HTTP when neither is. Mount them
# read-only at the default paths below (or point TLS_CERT_FILE / TLS_KEY_FILE / TLS_CA_FILE elsewhere):
#   /etc/openjcockpit/tls/tls.crt   certificate, leaf first, followed by any intermediates
#   /etc/openjcockpit/tls/tls.key   private key (PKCS#1, PKCS#8 or SEC1); TLS_KEY_PASSWORD if encrypted
#   /etc/openjcockpit/tls/ca.crt    optional: extra CA certificates to trust (e.g. a private root CA)
# All files must be readable by the container user (UID 10001). Only one of certificate and key present is
# a misconfiguration and stops the container instead of silently falling back to HTTP.
#
# Where mounting files is not possible, pass the same material through the environment instead: TLS_CERT,
# TLS_KEY and TLS_CA hold the PEM text itself (or that PEM text base64-encoded, for platforms without
# multi-line variables). It is written to a private directory under /tmp and used exactly like the files
# above; the variables are removed before the JVM starts. A variable and its file (mounted or *_FILE) both
# set is a misconfiguration. Material from the environment is not reloaded: a new certificate needs a restart.
#
# TLS applies to the application port only. A separate management port (MANAGEMENT_PORT, used by probes
# and the metrics scraper and never routed externally) stays HTTP unless MANAGEMENT_SERVER_SSL_ENABLED=true.
# The certificate files are watched, so a renewed certificate is served without a restart.
#
# The certificates of tls.crt and ca.crt are added to a copy of the JDK's default truststore, which the JVM
# then uses for outgoing TLS, so services can call each other with self-signed or private-CA certificates.
# The truststore is built at startup: a renewed certificate is trusted after the next restart.
set -euo pipefail

cert="${TLS_CERT_FILE:-/etc/openjcockpit/tls/tls.crt}"
key="${TLS_KEY_FILE:-/etc/openjcockpit/tls/tls.key}"
ca="${TLS_CA_FILE:-/etc/openjcockpit/tls/ca.crt}"

# Usage: from_env <content variable> <file variable> <path variable> <file name>. When the content variable
# is set, writes its PEM (decoding base64 first if needed) to a private file and points the path variable at it.
material_dir=""
from_env() {
  local content="${!1:-}"
  [[ -n "$content" ]] || return 0
  if [[ -n "${!2:-}" ]]; then
    echo "TLS misconfigured: $1 and $2 are both set; use one of them" >&2
    exit 1
  fi
  if [[ -e "${!3}" ]]; then
    echo "TLS misconfigured: $1 is set and ${!3} is mounted; use the variable or the file, not both" >&2
    exit 1
  fi
  if [[ "$content" != *"-----BEGIN "* ]]; then
    if ! content="$(printf '%s' "$content" | base64 -d 2>/dev/null)" || [[ "$content" != *"-----BEGIN "* ]]; then
      echo "TLS misconfigured: $1 is neither PEM nor base64-encoded PEM" >&2
      exit 1
    fi
  fi
  if [[ -z "$material_dir" ]]; then
    material_dir="$(mktemp -d)"
  fi
  (umask 077 && printf '%s\n' "$content" > "$material_dir/$4")
  printf -v "$3" '%s' "$material_dir/$4"
}
from_env TLS_CERT TLS_CERT_FILE cert tls.crt
from_env TLS_KEY TLS_KEY_FILE key tls.key
from_env TLS_CA TLS_CA_FILE ca ca.crt
unset TLS_CERT TLS_KEY TLS_CA

require_readable() {
  if [[ ! -e "$1" ]]; then
    echo "TLS misconfigured: $1 is missing${2:+ ($2)}" >&2
    exit 1
  fi
  if [[ ! -r "$1" ]]; then
    echo "TLS misconfigured: $1 is not readable by UID $(id -u)" >&2
    exit 1
  fi
}

trusted=()
if [[ -e "$cert" || -e "$key" ]]; then
  require_readable "$cert" "certificate and private key must both be mounted"
  require_readable "$key" "certificate and private key must both be mounted"
  echo "TLS enabled: serving HTTPS with certificate $cert" >&2
  export SERVER_SSL_BUNDLE=server
  export SPRING_SSL_BUNDLE_PEM_SERVER_KEYSTORE_CERTIFICATE="file:$cert"
  export SPRING_SSL_BUNDLE_PEM_SERVER_KEYSTORE_PRIVATEKEY="file:$key"
  export SPRING_SSL_BUNDLE_PEM_SERVER_RELOADONUPDATE=true
  if [[ -n "${TLS_KEY_PASSWORD:-}" ]]; then
    export SPRING_SSL_BUNDLE_PEM_SERVER_KEYSTORE_PRIVATEKEYPASSWORD="$TLS_KEY_PASSWORD"
  fi
  export MANAGEMENT_SERVER_SSL_ENABLED="${MANAGEMENT_SERVER_SSL_ENABLED:-false}"
  trusted+=("$cert")
else
  echo "TLS disabled: no certificate at $cert, serving HTTP" >&2
fi
if [[ -e "$ca" ]]; then
  require_readable "$ca"
  trusted+=("$ca")
fi

java_opts=(-XX:MaxRAMPercentage=75)
if (( ${#trusted[@]} > 0 )); then
  # The truststore holds public certificates only; its password is the JDK's well-known default.
  truststore="$(mktemp -d)/truststore.p12"
  storepass=changeit
  cp "$JAVA_HOME/lib/security/cacerts" "$truststore"
  chmod u+w "$truststore"
  # keytool imports one certificate per call, so split each PEM bundle into its certificates first.
  split_dir="$(mktemp -d)"
  awk -v dir="$split_dir" '
    /-----BEGIN CERTIFICATE-----/ { n++; out = sprintf("%s/%03d.pem", dir, n) }
    out { print > out }
    /-----END CERTIFICATE-----/ { close(out); out = "" }
  ' "${trusted[@]}"
  count=0
  for pem in "$split_dir"/*.pem; do
    [[ -e "$pem" ]] || continue
    count=$((count + 1))
    keytool -importcert -noprompt -keystore "$truststore" -storepass "$storepass" \
      -alias "openjcockpit-$(basename "$pem" .pem)" -file "$pem" >/dev/null
  done
  rm -rf "$split_dir"
  if (( count == 0 )); then
    echo "TLS misconfigured: no PEM certificates found in ${trusted[*]}" >&2
    exit 1
  fi
  echo "TLS trust: added $count certificate(s) from ${trusted[*]} to the JVM truststore" >&2
  java_opts+=(
    "-Djavax.net.ssl.trustStore=$truststore"
    "-Djavax.net.ssl.trustStorePassword=$storepass"
    "-Djavax.net.ssl.trustStoreType=PKCS12"
  )
fi

exec java "${java_opts[@]}" -jar /app/app.jar "$@"
