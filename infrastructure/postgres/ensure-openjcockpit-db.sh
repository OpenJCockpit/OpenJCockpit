#!/bin/sh
set -e

psql -h postgres -U keycloak -d keycloak -v ON_ERROR_STOP=1 <<-'EOSQL'
  DO $$
  BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'openjcockpit_app') THEN
      CREATE USER openjcockpit_app WITH PASSWORD 'openjcockpit_app';
    END IF;
  END
  $$;
EOSQL

if ! psql -h postgres -U keycloak -d keycloak -tAc "SELECT 1 FROM pg_database WHERE datname='openjcockpit'" | grep -q 1; then
  psql -h postgres -U keycloak -d keycloak -c "CREATE DATABASE openjcockpit OWNER openjcockpit_app"
  psql -h postgres -U keycloak -d keycloak -c "GRANT ALL PRIVILEGES ON DATABASE openjcockpit TO openjcockpit_app"
fi

echo "openjcockpit database ready"

psql -h postgres -U keycloak -d keycloak -v ON_ERROR_STOP=1 <<-'EOSQL'
  DO $$
  BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'embabel_app') THEN
      CREATE USER embabel_app WITH PASSWORD 'embabel_app';
    END IF;
  END
  $$;
EOSQL

if ! psql -h postgres -U keycloak -d keycloak -tAc "SELECT 1 FROM pg_database WHERE datname='embabel'" | grep -q 1; then
  psql -h postgres -U keycloak -d keycloak -c "CREATE DATABASE embabel OWNER embabel_app"
  psql -h postgres -U keycloak -d keycloak -c "GRANT ALL PRIVILEGES ON DATABASE embabel TO embabel_app"
fi

echo "embabel database ready"

psql -h postgres -U keycloak -d keycloak -v ON_ERROR_STOP=1 <<-'EOSQL'
  DO $$
  BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'litellm_app') THEN
      CREATE USER litellm_app WITH PASSWORD 'litellm_app';
    END IF;
  END
  $$;
EOSQL

if ! psql -h postgres -U keycloak -d keycloak -tAc "SELECT 1 FROM pg_database WHERE datname='litellm'" | grep -q 1; then
  psql -h postgres -U keycloak -d keycloak -c "CREATE DATABASE litellm OWNER litellm_app"
  psql -h postgres -U keycloak -d keycloak -c "GRANT ALL PRIVILEGES ON DATABASE litellm TO litellm_app"
fi

echo "litellm database ready"
