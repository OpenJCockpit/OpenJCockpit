#!/bin/sh
# Idempotently seeds gitignored, per-developer Keycloak users into the
# metafactory realm from infrastructure/keycloak/local-users/*.local.json.
# See infrastructure/keycloak/local-users/README.md for the supported format.
set -e

# Always clean up temp files, on every exit path (success or failure).
# /tmp is container-local for this one-shot container and never a bind mount.
trap 'rm -f /tmp/kc-local-users.tsv /tmp/kc-resp.json' EXIT

KC_URL="${KC_URL:-http://keycloak:8080}"
KC_REALM="${KC_REALM:-metafactory}"
KC_LOCAL_USERS_DIR="${KC_LOCAL_USERS_DIR:-/local-users}"

# 1. No-op guard: busybox sh has no nullglob, so an unmatched glob stays literal.
set -- "$KC_LOCAL_USERS_DIR"/*.local.json
if [ ! -e "$1" ]; then
  echo "keycloak-local-users: no *.local.json files found, nothing to do"
  exit 0
fi

if [ -z "$KC_BOOTSTRAP_ADMIN_USERNAME" ] || [ -z "$KC_BOOTSTRAP_ADMIN_PASSWORD" ]; then
  echo "keycloak-local-users: KC_BOOTSTRAP_ADMIN_USERNAME / KC_BOOTSTRAP_ADMIN_PASSWORD must be set"
  exit 1
fi

# 2. Admin token (master realm, admin-cli, password grant). Never printed.
TOKEN=$(curl -s -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "client_id=admin-cli" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${KC_BOOTSTRAP_ADMIN_USERNAME}" \
  --data-urlencode "password=${KC_BOOTSTRAP_ADMIN_PASSWORD}" \
  "${KC_URL}/realms/master/protocol/openid-connect/token" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "keycloak-local-users: failed to obtain admin token"
  exit 1
fi

# 3. Parse every *.local.json file with one awk pass per file into a fixed
# tab-separated intermediate: username, email, firstName, lastName, enabled,
# emailVerified, password, roles(comma-separated). Empty field when absent.
: > /tmp/kc-local-users.tsv

for f in "$@"; do
  awk -v fname="$f" '
    function skipws() {
      while (i <= n) {
        c = substr(content, i, 1)
        if (c == " " || c == "\t" || c == "\n" || c == "\r") { i++ } else { break }
      }
    }
    function expect(ch) {
      skipws()
      if (substr(content, i, 1) != ch) {
        printf "%s: expected [%s] at position %d\n", fname, ch, i > "/dev/stderr"
        exit 1
      }
      i++
    }
    function parse_string(   sc, out) {
      skipws()
      if (substr(content, i, 1) != "\"") {
        printf "%s: expected a string value at position %d\n", fname, i > "/dev/stderr"
        exit 1
      }
      i++
      out = ""
      while (i <= n) {
        sc = substr(content, i, 1)
        if (sc == "\"") { i++; return out }
        if (sc == "\\") {
          printf "%s: unsupported escape sequence in a string value\n", fname > "/dev/stderr"
          exit 1
        }
        if (sc == "\t" || sc == "\n" || sc == "\r") {
          printf "%s: string value contains a tab or newline\n", fname > "/dev/stderr"
          exit 1
        }
        out = out sc
        i++
      }
      printf "%s: unterminated string value\n", fname > "/dev/stderr"
      exit 1
    }
    function parse_bool(   ) {
      skipws()
      if (substr(content, i, 4) == "true") { i += 4; return "true" }
      if (substr(content, i, 5) == "false") { i += 5; return "false" }
      printf "%s: expected true or false at position %d\n", fname, i > "/dev/stderr"
      exit 1
    }
    function parse_string_array(   arr, count, s, ch) {
      arr = ""
      count = 0
      expect("[")
      skipws()
      if (substr(content, i, 1) == "]") { i++; return arr }
      while (1) {
        s = parse_string()
        if (count == 0) { arr = s } else { arr = arr "," s }
        count++
        skipws()
        ch = substr(content, i, 1)
        if (ch == ",") { i++; continue }
        if (ch == "]") { i++; break }
        printf "%s: expected comma or closing bracket in realmRoles\n", fname > "/dev/stderr"
        exit 1
      }
      return arr
    }
    function parse_user(   key, username, email, first, last, enabled, verified, password, roles, sawUsername, ch) {
      username = ""; email = ""; first = ""; last = ""
      enabled = "true"; verified = "true"; password = ""; roles = ""
      sawUsername = 0
      expect("{")
      skipws()
      if (substr(content, i, 1) == "}") {
        i++
        printf "%s: user object is missing the required key username\n", fname > "/dev/stderr"
        exit 1
      }
      while (1) {
        key = parse_string()
        expect(":")
        if (key == "username") { username = parse_string(); sawUsername = 1 }
        else if (key == "email") { email = parse_string() }
        else if (key == "firstName") { first = parse_string() }
        else if (key == "lastName") { last = parse_string() }
        else if (key == "password") { password = parse_string() }
        else if (key == "enabled") { enabled = parse_bool() }
        else if (key == "emailVerified") { verified = parse_bool() }
        else if (key == "realmRoles") { roles = parse_string_array() }
        else {
          printf "%s: unknown key %s in user object\n", fname, key > "/dev/stderr"
          exit 1
        }
        skipws()
        ch = substr(content, i, 1)
        if (ch == ",") { i++; continue }
        if (ch == "}") { i++; break }
        printf "%s: expected comma or closing brace in user object at position %d\n", fname, i > "/dev/stderr"
        exit 1
      }
      if (sawUsername == 0 || username == "") {
        printf "%s: user object is missing the required key username\n", fname > "/dev/stderr"
        exit 1
      }
      printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", username, email, first, last, enabled, verified, password, roles
    }
    { content = content $0 "\n" }
    END {
      i = 1
      n = length(content)
      expect("[")
      skipws()
      if (substr(content, i, 1) == "]") {
        i++
      } else {
        while (1) {
          parse_user()
          skipws()
          ch = substr(content, i, 1)
          if (ch == ",") { i++; continue }
          if (ch == "]") { i++; break }
          printf "%s: expected comma or closing bracket after user object\n", fname > "/dev/stderr"
          exit 1
        }
      }
      skipws()
      if (i <= n) {
        printf "%s: unexpected trailing content after the JSON array\n", fname > "/dev/stderr"
        exit 1
      }
    }
  ' "$f" >> /tmp/kc-local-users.tsv
done

# 4-7. Reconcile each parsed user against the admin REST API.
user_count=0

while IFS="$(printf '\t')" read -r username email first last enabled verified password roles; do
  user_count=$((user_count + 1))

  # 4. Look up by exact username.
  status=$(curl -s -G -o /tmp/kc-resp.json -w '%{http_code}' \
    -H "Authorization: Bearer ${TOKEN}" \
    --data-urlencode "username=${username}" \
    --data-urlencode "exact=true" \
    "${KC_URL}/admin/realms/${KC_REALM}/users")
  if [ "$status" != "200" ]; then
    echo "keycloak-local-users: GET /admin/realms/${KC_REALM}/users -> ${status} (user: ${username})"
    exit 1
  fi
  id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' /tmp/kc-resp.json | head -n1)

  base_payload=$(printf '{"username":"%s","email":"%s","firstName":"%s","lastName":"%s","enabled":%s,"emailVerified":%s}' \
    "$username" "$email" "$first" "$last" "$enabled" "$verified")

  if [ -z "$id" ]; then
    # 5. Absent -> create. Password (if any) goes in the credentials array,
    # never as a flat "password" key; realmRoles are never sent here.
    if [ -n "$password" ]; then
      create_payload=$(printf '{"username":"%s","email":"%s","firstName":"%s","lastName":"%s","enabled":%s,"emailVerified":%s,"credentials":[{"type":"password","value":"%s","temporary":false}]}' \
        "$username" "$email" "$first" "$last" "$enabled" "$verified" "$password")
    else
      create_payload="$base_payload"
    fi
    status=$(curl -s -o /tmp/kc-resp.json -w '%{http_code}' \
      -H "Authorization: Bearer ${TOKEN}" \
      -H "Content-Type: application/json" \
      -X POST -d "$create_payload" \
      "${KC_URL}/admin/realms/${KC_REALM}/users")
    if [ "$status" != "201" ]; then
      echo "keycloak-local-users: POST /admin/realms/${KC_REALM}/users -> ${status} (user: ${username})"
      exit 1
    fi

    # Re-look-up to obtain the new id (simpler and more robust than parsing Location).
    status=$(curl -s -G -o /tmp/kc-resp.json -w '%{http_code}' \
      -H "Authorization: Bearer ${TOKEN}" \
      --data-urlencode "username=${username}" \
      --data-urlencode "exact=true" \
      "${KC_URL}/admin/realms/${KC_REALM}/users")
    if [ "$status" != "200" ]; then
      echo "keycloak-local-users: GET /admin/realms/${KC_REALM}/users -> ${status} (user: ${username})"
      exit 1
    fi
    id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' /tmp/kc-resp.json | head -n1)
    if [ -z "$id" ]; then
      echo "keycloak-local-users: created user ${username} but could not resolve its id"
      exit 1
    fi
  else
    # 6. Present -> reconcile fields, then reset the password if one was given.
    status=$(curl -s -o /tmp/kc-resp.json -w '%{http_code}' \
      -H "Authorization: Bearer ${TOKEN}" \
      -H "Content-Type: application/json" \
      -X PUT -d "$base_payload" \
      "${KC_URL}/admin/realms/${KC_REALM}/users/${id}")
    if [ "$status" != "204" ]; then
      echo "keycloak-local-users: PUT /admin/realms/${KC_REALM}/users/${id} -> ${status} (user: ${username})"
      exit 1
    fi

    if [ -n "$password" ]; then
      reset_payload=$(printf '{"type":"password","value":"%s","temporary":false}' "$password")
      status=$(curl -s -o /tmp/kc-resp.json -w '%{http_code}' \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -X PUT -d "$reset_payload" \
        "${KC_URL}/admin/realms/${KC_REALM}/users/${id}/reset-password")
      if [ "$status" != "204" ]; then
        echo "keycloak-local-users: PUT /admin/realms/${KC_REALM}/users/${id}/reset-password -> ${status} (user: ${username})"
        exit 1
      fi
    fi
  fi

  # 7. Roles: skip entirely when realmRoles was omitted or empty.
  if [ -n "$roles" ]; then
    old_ifs="$IFS"
    IFS=","
    set -- $roles
    IFS="$old_ifs"
    for role in "$@"; do
      status=$(curl -s -o /tmp/kc-resp.json -w '%{http_code}' \
        -H "Authorization: Bearer ${TOKEN}" \
        "${KC_URL}/admin/realms/${KC_REALM}/roles/${role}")
      if [ "$status" = "404" ]; then
        echo "keycloak-local-users: unknown realm role '${role}' for user '${username}'"
        exit 1
      fi
      if [ "$status" != "200" ]; then
        echo "keycloak-local-users: GET /admin/realms/${KC_REALM}/roles/${role} -> ${status} (user: ${username})"
        exit 1
      fi
      role_id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' /tmp/kc-resp.json | head -n1)
      if [ -z "$role_id" ]; then
        echo "keycloak-local-users: could not resolve id of realm role '${role}' for user '${username}'"
        exit 1
      fi
      mapping_payload=$(printf '[{"id":"%s","name":"%s"}]' "$role_id" "$role")
      status=$(curl -s -o /tmp/kc-resp.json -w '%{http_code}' \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -X POST -d "$mapping_payload" \
        "${KC_URL}/admin/realms/${KC_REALM}/users/${id}/role-mappings/realm")
      if [ "$status" != "204" ]; then
        echo "keycloak-local-users: POST /admin/realms/${KC_REALM}/users/${id}/role-mappings/realm -> ${status} (user: ${username}, role: ${role})"
        exit 1
      fi
    done
  fi
done < /tmp/kc-local-users.tsv

# 8. Summary.
echo "keycloak-local-users: ${user_count} user(s) ready"
