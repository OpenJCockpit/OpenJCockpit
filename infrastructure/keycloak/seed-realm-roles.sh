#!/bin/sh
# Idempotently ensures the OpenJCockpit realm roles exist, maps them to the configured
# (local dev) users, and keeps them out of the realm's default role so a new user is
# granted nothing. Needed because --import-realm is skipped for an existing realm.
#
# Roles (implications are enforced by the services' role hierarchy, not by Keycloak
# composites; see docs/readme/governance/authorization-model.md):
#   openjcockpit-admin     everything (implies operator, approver, viewer)
#   openjcockpit-operator  start/stop runs; manage workflows, specs, queues (implies viewer)
#   openjcockpit-approver  decide approval gates (implies viewer)
#   openjcockpit-viewer    read-only
#
# Users per role are space-separated usernames (env, defaults are the local dev users):
#   KC_ADMIN_ROLE_USERS     (default: tony)        missing user -> error
#   KC_OPERATOR_ROLE_USERS  (default: koen ricky e2e)
#   KC_APPROVER_ROLE_USERS  (default: koen e2e)
#   KC_VIEWER_ROLE_USERS    (default: none)
# A missing non-admin user is skipped with a warning (e.g. e2e is seeded by another
# one-shot container that may run later; seed-e2e-user.sh maps its roles itself).
set -e

RESP=/tmp/kc-realm-roles-resp.json
trap 'rm -f "$RESP"' EXIT

KC_URL="${KC_URL:-http://keycloak:8080}"
KC_REALM="${KC_REALM:-openjcockpit}"
KC_ADMIN_ROLE_NAME="${KC_ADMIN_ROLE_NAME:-openjcockpit-admin}"
KC_ADMIN_ROLE_USERS="${KC_ADMIN_ROLE_USERS-tony}"
KC_OPERATOR_ROLE_USERS="${KC_OPERATOR_ROLE_USERS-koen ricky e2e}"
KC_APPROVER_ROLE_USERS="${KC_APPROVER_ROLE_USERS-koen e2e}"
KC_VIEWER_ROLE_USERS="${KC_VIEWER_ROLE_USERS-}"
DEFAULT_ROLE="default-roles-${KC_REALM}"

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

ADMIN_API="${KC_URL}/admin/realms/${KC_REALM}"

# ensure_role <name> <description>: creates the role when absent; sets ROLE_ID.
ensure_role() {
  role_url="${ADMIN_API}/roles/$1"
  status=$(curl -s -o "$RESP" -w '%{http_code}' -H "Authorization: Bearer ${TOKEN}" "$role_url")
  if [ "$status" = "404" ]; then
    payload=$(printf '{"name":"%s","description":"%s"}' "$1" "$2")
    status=$(curl -s -o "$RESP" -w '%{http_code}' \
      -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
      -X POST -d "$payload" "${ADMIN_API}/roles")
    if [ "$status" != "201" ] && [ "$status" != "409" ]; then
      echo "keycloak-realm-roles: POST ${ADMIN_API}/roles ($1) -> ${status}"
      exit 1
    fi
    status=$(curl -s -o "$RESP" -w '%{http_code}' -H "Authorization: Bearer ${TOKEN}" "$role_url")
  fi
  if [ "$status" != "200" ]; then
    echo "keycloak-realm-roles: GET ${role_url} -> ${status}"
    exit 1
  fi
  ROLE_ID=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$RESP" | head -n1)
  if [ -z "$ROLE_ID" ]; then
    echo "keycloak-realm-roles: could not resolve id of role $1"
    exit 1
  fi
}

# ensure_not_default <name> <id>: removes the role from the realm's default role (deny by default).
ensure_not_default() {
  composites_url="${ADMIN_API}/roles/${DEFAULT_ROLE}/composites"
  status=$(curl -s -o "$RESP" -w '%{http_code}' -H "Authorization: Bearer ${TOKEN}" "$composites_url")
  if [ "$status" != "200" ]; then
    echo "keycloak-realm-roles: GET ${composites_url} -> ${status} (default role not checked)"
    return 0
  fi
  if grep -q "\"name\":\"$1\"" "$RESP"; then
    mapping=$(printf '[{"id":"%s","name":"%s"}]' "$2" "$1")
    status=$(curl -s -o "$RESP" -w '%{http_code}' \
      -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
      -X DELETE -d "$mapping" "$composites_url")
    if [ "$status" != "204" ]; then
      echo "keycloak-realm-roles: DELETE $1 from ${DEFAULT_ROLE} -> ${status}"
      exit 1
    fi
    echo "keycloak-realm-roles: removed $1 from ${DEFAULT_ROLE}"
  fi
}

# map_users <role> <id> <required:true|false> <users...>: idempotent realm-role mapping.
map_users() {
  role="$1"; id="$2"; required="$3"; shift 3
  for user in "$@"; do
    if [ "$role" = "$KC_ADMIN_ROLE_NAME" ] && [ "$user" = "e2e" ]; then
      echo "keycloak-realm-roles: refusing to grant ${role} to the e2e user"
      exit 1
    fi
    status=$(curl -s -G -o "$RESP" -w '%{http_code}' \
      -H "Authorization: Bearer ${TOKEN}" \
      --data-urlencode "username=${user}" --data-urlencode "exact=true" \
      "${ADMIN_API}/users")
    user_id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$RESP" | head -n1)
    if [ "$status" != "200" ] || [ -z "$user_id" ]; then
      if [ "$required" = "true" ]; then
        echo "keycloak-realm-roles: user not found: ${user} (GET users -> ${status})"
        exit 1
      fi
      echo "keycloak-realm-roles: user not found, skipping ${role} for ${user} (GET users -> ${status})"
      continue
    fi
    mapping=$(printf '[{"id":"%s","name":"%s"}]' "$id" "$role")
    status=$(curl -s -o "$RESP" -w '%{http_code}' \
      -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
      -X POST -d "$mapping" \
      "${ADMIN_API}/users/${user_id}/role-mappings/realm")
    if [ "$status" != "204" ]; then
      echo "keycloak-realm-roles: POST role-mappings ${role} for ${user} -> ${status}"
      exit 1
    fi
  done
  echo "keycloak-realm-roles: role '${role}' ready; mapped to: ${*:-nobody}"
}

ensure_role "$KC_ADMIN_ROLE_NAME" "OpenJCockpit administrator: everything, including project, git-credential, OPA, integration, skills-marketplace and A2A configuration. Implies operator, approver and viewer."
ensure_not_default "$KC_ADMIN_ROLE_NAME" "$ROLE_ID"
# shellcheck disable=SC2086 # word splitting of the user lists is intended
map_users "$KC_ADMIN_ROLE_NAME" "$ROLE_ID" true $KC_ADMIN_ROLE_USERS

ensure_role openjcockpit-operator "OpenJCockpit operator: start/stop workflow runs; manage workflows, definitions, specs and spec queues. Implies viewer."
ensure_not_default openjcockpit-operator "$ROLE_ID"
# shellcheck disable=SC2086
map_users openjcockpit-operator "$ROLE_ID" false $KC_OPERATOR_ROLE_USERS

ensure_role openjcockpit-approver "OpenJCockpit approver: decide workflow approval gates. Implies viewer."
ensure_not_default openjcockpit-approver "$ROLE_ID"
# shellcheck disable=SC2086
map_users openjcockpit-approver "$ROLE_ID" false $KC_APPROVER_ROLE_USERS

ensure_role openjcockpit-viewer "OpenJCockpit viewer: read-only access to the dashboard API."
ensure_not_default openjcockpit-viewer "$ROLE_ID"
# shellcheck disable=SC2086
map_users openjcockpit-viewer "$ROLE_ID" false $KC_VIEWER_ROLE_USERS
