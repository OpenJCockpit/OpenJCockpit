#!/bin/sh
# Idempotently seeds the fixed E2E test user into the openjcockpit realm.
set -e

# Always clean up temp files, on every exit path (success or failure).
trap 'rm -f /tmp/kc-e2e-user-resp.json' EXIT

KC_URL="${KC_URL:-http://keycloak:8080}"
KC_REALM="${KC_REALM:-openjcockpit}"

if [ -z "$KC_BOOTSTRAP_ADMIN_USERNAME" ] || [ -z "$KC_BOOTSTRAP_ADMIN_PASSWORD" ]; then
  echo "keycloak-e2e-user: KC_BOOTSTRAP_ADMIN_USERNAME / KC_BOOTSTRAP_ADMIN_PASSWORD must be set"
  exit 1
fi

E2E_SEED_USERNAME="${E2E_SEED_USERNAME:-e2e}"
E2E_SEED_EMAIL="${E2E_SEED_EMAIL:-e2e@openjcockpit.local}"
E2E_SEED_PASSWORD="${E2E_SEED_PASSWORD:-E2eRunner01!}"

# Fixed fields, not configurable
FIRST_NAME="E2E"
LAST_NAME="Runner"
ENABLED="true"
VERIFIED="true"

TOKEN=$(curl -s -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "client_id=admin-cli" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${KC_BOOTSTRAP_ADMIN_USERNAME}" \
  --data-urlencode "password=${KC_BOOTSTRAP_ADMIN_PASSWORD}" \
  "${KC_URL}/realms/master/protocol/openid-connect/token" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "keycloak-e2e-user: failed to obtain admin token"
  exit 1
fi

# Look up by exact username.
status=$(curl -s -G -o /tmp/kc-e2e-user-resp.json -w '%{http_code}' \
  -H "Authorization: Bearer ${TOKEN}" \
  --data-urlencode "username=${E2E_SEED_USERNAME}" \
  --data-urlencode "exact=true" \
  "${KC_URL}/admin/realms/${KC_REALM}/users")
if [ "$status" != "200" ]; then
  echo "keycloak-e2e-user: GET /admin/realms/${KC_REALM}/users -> ${status} (user: ${E2E_SEED_USERNAME})"
  exit 1
fi
id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' /tmp/kc-e2e-user-resp.json | head -n1)

base_payload=$(printf '{"username":"%s","email":"%s","firstName":"%s","lastName":"%s","enabled":%s,"emailVerified":%s}' \
  "$E2E_SEED_USERNAME" "$E2E_SEED_EMAIL" "$FIRST_NAME" "$LAST_NAME" "$ENABLED" "$VERIFIED")

if [ -z "$id" ]; then
  # Create with credentials array.
  create_payload=$(printf '{"username":"%s","email":"%s","firstName":"%s","lastName":"%s","enabled":%s,"emailVerified":%s,"credentials":[{"type":"password","value":"%s","temporary":false}]}' \
    "$E2E_SEED_USERNAME" "$E2E_SEED_EMAIL" "$FIRST_NAME" "$LAST_NAME" "$ENABLED" "$VERIFIED" "$E2E_SEED_PASSWORD")
  status=$(curl -s -o /tmp/kc-e2e-user-resp.json -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -X POST -d "$create_payload" \
    "${KC_URL}/admin/realms/${KC_REALM}/users")
  if [ "$status" != "201" ]; then
    echo "keycloak-e2e-user: POST /admin/realms/${KC_REALM}/users -> ${status} (user: ${E2E_SEED_USERNAME})"
    exit 1
  fi
  # Re-look-up to obtain the new id.
  status=$(curl -s -G -o /tmp/kc-e2e-user-resp.json -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" \
    --data-urlencode "username=${E2E_SEED_USERNAME}" \
    --data-urlencode "exact=true" \
    "${KC_URL}/admin/realms/${KC_REALM}/users")
  if [ "$status" != "200" ]; then
    echo "keycloak-e2e-user: GET /admin/realms/${KC_REALM}/users -> ${status} (user: ${E2E_SEED_USERNAME})"
    exit 1
  fi
  id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' /tmp/kc-e2e-user-resp.json | head -n1)
  if [ -z "$id" ]; then
    echo "keycloak-e2e-user: created user ${E2E_SEED_USERNAME} but could not resolve its id"
    exit 1
  fi
else
  # Present -> reconcile fields, then reset the password.
  status=$(curl -s -o /tmp/kc-e2e-user-resp.json -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -X PUT -d "$base_payload" \
    "${KC_URL}/admin/realms/${KC_REALM}/users/${id}")
  if [ "$status" != "204" ]; then
    echo "keycloak-e2e-user: PUT /admin/realms/${KC_REALM}/users/${id} -> ${status} (user: ${E2E_SEED_USERNAME})"
    exit 1
  fi

  reset_payload=$(printf '{"type":"password","value":"%s","temporary":false}' "$E2E_SEED_PASSWORD")
  status=$(curl -s -o /tmp/kc-e2e-user-resp.json -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -X PUT -d "$reset_payload" \
    "${KC_URL}/admin/realms/${KC_REALM}/users/${id}/reset-password")
  if [ "$status" != "204" ]; then
    echo "keycloak-e2e-user: PUT /admin/realms/${KC_REALM}/users/${id}/reset-password -> ${status} (user: ${E2E_SEED_USERNAME})"
    exit 1
  fi
fi

echo "keycloak-e2e-user: user '${E2E_SEED_USERNAME}' ready"
