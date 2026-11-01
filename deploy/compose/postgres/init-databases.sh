#!/bin/sh
# First-start initialisation of the production PostgreSQL instance (docker-entrypoint-initdb.d): one
# database and one least-privilege owner per component. Passwords come from the environment only; the
# script runs once, on an empty data directory. Rotation: ALTER ROLE ... PASSWORD (see the runbook).
set -eu

create() {  # $1 role, $2 database, $3 password
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       -v role="$1" -v db="$2" -v pw="$3" <<'EOSQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE', :'role', :'pw') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'role') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'db') \gexec
EOSQL
  echo "init-databases: ${2} ready"
}

create keycloak_app     keycloak     "$KEYCLOAK_DB_PASSWORD"
create openjcockpit_app openjcockpit "$OPENJCOCKPIT_DB_PASSWORD"
create embabel_app      embabel      "$EMBABEL_DB_PASSWORD"
create litellm_app      litellm      "$LITELLM_DB_PASSWORD"
