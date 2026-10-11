#!/bin/sh
# Idempotently ensures the openjcockpit-admin realm role exists and maps it to the
# configured users. Needed because --import-realm is skipped for an existing realm.
set -e

RESP=/tmp/kc-realm-roles-resp.json
trap 'rm -f "$RESP"' EXIT

KC_URL="${KC_URL:-http://keycloak:8080}"
KC_REALM="${KC_REALM:-openjcockpit}"
KC_ADMIN_ROLE_NAME="${KC_ADMIN_ROLE_NAME:-openjcockpit-admin}"
KC_ADMIN_ROLE_USERS="${KC_ADMIN_ROLE_USERS:-tony}"
ROLE_DESCRIPTION="May change project-level spec-queue settings (autoMergeAllowed). Local seed only."

if [ -z "$KC_BOOTSTRAP_ADMIN_USERNAME" ] || [ -z "$KC_BOOTSTRAP_ADMIN_PASSWORD" ]; then
  echo "keycloak-realm-roles: KC_BOOTSTRAP_ADMIN_USERNAME / KC_BOOTSTRAP_ADMIN_PASSWORD must be set"
  exit 1
fi

TOKEN=$(curl -s -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "client_id=admin-cli" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${KC_BOOTSTRAP_ADMIN_USERNAME}" \
  --data-urlencode "password=${KC_BOOTSTRAP_ADMIN_PASSWORD}" \
  "${KC_URL}/realms/master/protocol/openid-connect/token" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "keycloak-realm-roles: failed to obtain admin token"
  exit 1
fi

ROLE_URL="${KC_URL}/admin/realms/${KC_REALM}/roles/${KC_ADMIN_ROLE_NAME}"

status=$(curl -s -o "$RESP" -w '%{http_code}' -H "Authorization: Bearer ${TOKEN}" "$ROLE_URL")
if [ "$status" = "404" ]; then
  payload=$(printf '{"name":"%s","description":"%s"}' "$KC_ADMIN_ROLE_NAME" "$ROLE_DESCRIPTION")
  status=$(curl -s -o "$RESP" -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
    -X POST -d "$payload" "${KC_URL}/admin/realms/${KC_REALM}/roles")
  if [ "$status" != "201" ] && [ "$status" != "409" ]; then
    echo "keycloak-realm-roles: POST /admin/realms/${KC_REALM}/roles -> ${status}"
    exit 1
  fi
  status=$(curl -s -o "$RESP" -w '%{http_code}' -H "Authorization: Bearer ${TOKEN}" "$ROLE_URL")
fi
if [ "$status" != "200" ]; then
  echo "keycloak-realm-roles: GET ${ROLE_URL} -> ${status}"
  exit 1
fi

role_id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$RESP" | head -n1)
if [ -z "$role_id" ]; then
  echo "keycloak-realm-roles: could not resolve id of role ${KC_ADMIN_ROLE_NAME}"
  exit 1
fi

for user in $KC_ADMIN_ROLE_USERS; do
  if [ "$user" = "e2e" ]; then
    echo "keycloak-realm-roles: refusing to grant ${KC_ADMIN_ROLE_NAME} to the e2e user"
    exit 1
  fi
  status=$(curl -s -G -o "$RESP" -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" \
    --data-urlencode "username=${user}" --data-urlencode "exact=true" \
    "${KC_URL}/admin/realms/${KC_REALM}/users")
  user_id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$RESP" | head -n1)
  if [ "$status" != "200" ] || [ -z "$user_id" ]; then
    echo "keycloak-realm-roles: user not found: ${user} (GET users -> ${status})"
    exit 1
  fi
  mapping=$(printf '[{"id":"%s","name":"%s"}]' "$role_id" "$KC_ADMIN_ROLE_NAME")
  status=$(curl -s -o "$RESP" -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
    -X POST -d "$mapping" \
    "${KC_URL}/admin/realms/${KC_REALM}/users/${user_id}/role-mappings/realm")
  if [ "$status" != "204" ]; then
    echo "keycloak-realm-roles: POST role-mappings for ${user} -> ${status}"
    exit 1
  fi
done

echo "keycloak-realm-roles: role '${KC_ADMIN_ROLE_NAME}' ready; mapped to: ${KC_ADMIN_ROLE_USERS}"
