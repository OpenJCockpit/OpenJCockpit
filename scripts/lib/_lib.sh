#!/usr/bin/env bash
set -euo pipefail

mf::repo_root() {
  (cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
}

mf::_color() {
  # prints a color code only on a tty with NO_COLOR unset; else empty
  if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    printf '%s' "$1"
  fi
}

mf::log_step() { printf '%s[STEP]%s %s\n' "$(mf::_color '\033[1;36m')" "$(mf::_color '\033[0m')" "$1"; }
mf::log_info() { printf '%s[INFO]%s %s\n' "$(mf::_color '\033[1;32m')" "$(mf::_color '\033[0m')" "$1"; }
mf::log_warn() { printf '%s[WARN]%s %s\n' "$(mf::_color '\033[1;33m')" "$(mf::_color '\033[0m')" "$1" >&2; }
mf::log_error() { printf '%s[ERROR]%s %s\n' "$(mf::_color '\033[1;31m')" "$(mf::_color '\033[0m')" "$1" >&2; }

mf::die() {
  mf::log_error "$1"
  exit 1
}

mf::require_docker() {
  if ! docker info >/dev/null 2>&1; then
    mf::die "Docker daemon is not reachable. Start Docker Desktop (or the Docker service) and retry."
  fi
}

mf::require_env_file() {
  local root
  root="$(mf::repo_root)"
  if [ ! -f "$root/.env" ]; then
    mf::die "Missing required file: $root/.env (copy .env.example to .env and set OPENAI_API_KEY)"
  fi
}

mf::require_openai_key() {
  local prior_xtrace=""
  case "$-" in
    *x*) prior_xtrace="on" ;;
    *) prior_xtrace="off" ;;
  esac
  set +x

  local root example_file placeholder
  root="$(mf::repo_root)"
  example_file="$root/.env.example"

  if [ ! -f "$example_file" ]; then
    mf::die "Missing $example_file; cannot determine the OPENAI_API_KEY placeholder to validate against."
  fi

  placeholder="$(grep -E '^OPENAI_API_KEY=' "$example_file" | head -n 1 | cut -d '=' -f2-)"

  if [ -z "${OPENAI_API_KEY:-}" ]; then
    [ "$prior_xtrace" = "on" ] && set -x
    mf::die "OPENAI_API_KEY is not set. Set it in $root/.env before starting the stack."
  fi

  if [ "$OPENAI_API_KEY" = "$placeholder" ]; then
    [ "$prior_xtrace" = "on" ] && set -x
    mf::die "OPENAI_API_KEY in $root/.env is still the placeholder value from .env.example. Set a real key."
  fi

  [ "$prior_xtrace" = "on" ] && set -x
  return 0
}

mf::compose_file_args() {
  local args=(-f docker-compose.yml)
  if [ "${1:-}" = "--with-local-marketplace" ]; then
    args+=(-f docker-compose.local-marketplace.yml)
  fi
  printf '%s\n' "${args[@]}"
}

mf::wait_for_http() {
  local name="$1" url="$2" timeout_s="$3" interval_s="$4"
  local elapsed=0
  while [ "$elapsed" -lt "$timeout_s" ]; do
    if curl -fsS -o /dev/null --max-time 3 "$url"; then
      mf::log_info "$name is healthy ($url)"
      return 0
    fi
    sleep "$interval_s"
    elapsed=$((elapsed + interval_s))
  done
  mf::die "$name did not become healthy within ${timeout_s}s (probed $url)"
}

mf::print_urls() {
  mf::log_info "dashboard:              http://localhost:4000"
  mf::log_info "landing:                http://localhost:3000"
  mf::log_info "Keycloak admin:         http://localhost:8080/admin"
  mf::log_info "ai-control-service:     http://localhost:9080"
  mf::log_info "embabel-agent-service:  http://localhost:8091"
  mf::log_info "git-mcp-server:         http://localhost:8093"
  mf::log_info "OPA:                    http://localhost:8181"
  mf::log_info "LiteLLM gateway:        http://localhost:4100"
}

mf::confirm() {
  local expected="$1" reply=""
  printf 'Type "%s" to continue (anything else aborts): ' "$expected"
  read -r reply
  [ "$reply" = "$expected" ]
}

mf::acquire_lock() {
  local lock_dir="${TMPDIR:-/tmp}/metafactory-stack.lock"
  if ! mkdir "$lock_dir" 2>/dev/null; then
    mf::die "Another lifecycle script invocation appears to be in progress (lock: $lock_dir). Wait for it to finish or remove the lock manually if stale."
  fi
  trap 'rmdir "'"$lock_dir"'" 2>/dev/null || true' EXIT
}
