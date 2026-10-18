# Local (per-developer) Keycloak users

This directory lets you define your own, personal Keycloak users for local
development — without ever committing a personal account or password to git.

The committed `openjcockpit-realm.json` seeds three fixed, shared users
(`tony`, `koen`, `ricky`). If you want an extra login (your own name, a
throwaway QA account, a role combination you're testing), add a file here
instead of editing the realm file or clicking around in the Keycloak admin
console.

## Workflow

1. Copy the template and rename it to end in `.local.json`:

   ```sh
   cp infrastructure/keycloak/local-users/example.local.json.dist \
      infrastructure/keycloak/local-users/<dev>.local.json
   ```

2. Edit `<user>.local.json`: set `username`, `email`, `password`, and
   (optionally) `realmRoles`.
3. Apply it:

   ```sh
   docker compose up -d              # full stack, seeding runs automatically
   # or, to apply without restarting everything else:
   docker compose up keycloak-local-users
   ```

4. Log into the dashboard (or any client) with your new local user.

Re-running `docker compose up` (or `docker compose up keycloak-local-users`)
is safe: existing users are reconciled, not duplicated.

## Filename rule

The file **must** end in `.local.json`. This is both the glob the seed
script (`seed-local-users.sh`) looks for, and the pattern `.gitignore` uses
to exclude these files (`infrastructure/keycloak/local-users/*.local.json`).
Anything not matching that suffix (such as `example.local.json.dist`) is not
picked up by the seed script and stays a plain, committed file.

## Never commit these files

`*.local.json` files are gitignored, but treat that as belt-and-suspenders,
not a guarantee: never `git add -f` a personal user file, and never paste
its contents into a commit message, issue, or PR description. These files
are for **local development only** and may contain a plaintext password.

## Supported format

Each `*.local.json` file is a JSON array of user objects. A deliberately
narrow subset of the Keycloak admin REST API's user representation is
supported:

| Key             | Type             | Required | Notes                                                                  |
| --------------- | ---------------- | -------- | ---------------------------------------------------------------------- |
| `username`      | string           | yes      |                                                                        |
| `email`         | string           | no       |                                                                        |
| `firstName`     | string           | no       |                                                                        |
| `lastName`      | string           | no       |                                                                        |
| `enabled`       | boolean          | no       | defaults to `true`                                                     |
| `emailVerified` | boolean          | no       | defaults to `true`                                                     |
| `password`      | string           | no       | plaintext, local-dev only; set as a permanent (non-temporary) password |
| `realmRoles`    | array of strings | no       | defaults to empty; see below                                           |

Not supported, and rejected with an error if present: nested objects, a
`credentials` array, any key other than the ones above, and any string
value containing a double quote (`"`), a backslash (`\`), a tab, or a
newline.

Example:

```json
[
  {
    "username": "<dev>",
    "email": "<dev>@openjcockpit.local",
    "firstName": "<firstname>",
    "lastName": "<lastname>",
    "enabled": true,
    "emailVerified": true,
    "password": "changeit",
    "realmRoles": []
  }
]
```

## About `realmRoles`

`realmRoles` entries must already exist as realm roles in the `openjcockpit`
realm — the seed script fails loudly (naming the missing role) rather than
silently skipping it. The realm defines exactly one custom realm role,
`openjcockpit-admin`, which guards the admin-only spec-queue setting
`autoMergeAllowed`. Do not give local users that role unless you are testing
spec-queue settings; otherwise leave `realmRoles` as an empty array (`[]`).
There is deliberately no default role.

## Seeded admin role

`openjcockpit-admin` is assigned to `tony` by two paths:

1. The realm import (`openjcockpit-realm.json`), on a fresh Keycloak database.
2. The one-shot `keycloak-realm-roles` service (`seed-realm-roles.sh`), which
   covers existing databases where the import is skipped.

The seeder is idempotent (existing role and mappings are left alone), only
ever adds the role, and refuses to grant it to the `e2e` user. Re-run it with:

```sh
docker compose up --exit-code-from keycloak-realm-roles keycloak-realm-roles
```

## Scope

This mechanism is **local development only**. It is wired into
`docker-compose.yml` and talks to the local Keycloak instance using the same
bootstrap admin credentials already used to start that container. It is not
intended for, and must never be pointed at, a shared, staging, or production
Keycloak.

## Troubleshooting

- Check what the seed step did or why it failed:

  ```sh
  docker compose logs keycloak-local-users
  ```

- Re-run the seed step on its own (idempotent, safe to repeat):

  ```sh
  docker compose up keycloak-local-users
  ```

- If no `*.local.json` files are present, the step logs that there is
  nothing to do and exits successfully — this is the default, expected
  state on a fresh checkout.
- A failure naming an unknown realm role means a role listed in
  `realmRoles` does not exist in the `openjcockpit` realm; either remove it
  or add the role to the realm first.
- `docker compose up keycloak-local-users` always exits `0` at the shell
  level (that's standard Compose behavior for `up`, it doesn't propagate a
  one-shot service's own exit code). To detect a seeding failure from a
  script, use `docker compose up --exit-code-from keycloak-local-users
keycloak-local-users`, or check `docker compose ps keycloak-local-users`
  / the logs above.
- `keycloak-local-users` never starts: it waits for `keycloak-realm-roles` to
  exit successfully, so the role seeder exited non-zero (typically a user in
  `KC_ADMIN_ROLE_USERS` that does not exist). Diagnose with
  `docker compose ps -a keycloak-realm-roles` and
  `docker compose logs keycloak-realm-roles`. Fix the user or
  `KC_ADMIN_ROLE_USERS`, re-run the seeder (above), then
  `docker compose up keycloak-local-users`. No volume deletion needed.
