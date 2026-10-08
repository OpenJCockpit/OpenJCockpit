#!/bin/sh
set -e

# ── reset-workflow-definitions.sh ────────────────────────────────────────
# The ONLY script in this repository permitted to delete workflow-definition
# YAML files.  Two modes: inventory (read-only report) and clear (delete).
#
# Usage:
#   sh reset-workflow-definitions.sh [--require-mount] <inventory|clear> [ROOT]
#
# When ROOT is omitted the script falls back to $WORKFLOW_DEFINITIONS_PATH.
# When ROOT is supplied it overrides that env var (useful for unit tests).
# ─────────────────────────────────────────────────────────────────────────

CLEAR_DIRS="workflows workflow-groups"
PRESERVE_DIRS="agents subagents skills decision-logs approval-decisions"

# ── Parse flags and positional args ──────────────────────────────────────
REQUIRE_MOUNT=0
MODE=""
EXTRA_ROOT=""

while [ $# -gt 0 ]; do
  case "$1" in
    --require-mount)
      REQUIRE_MOUNT=1
      shift
      ;;
    inventory|clear)
      MODE="$1"
      shift
      ;;
    *)
      # First non-flag positional that is not a mode becomes the root override
      if [ -z "$EXTRA_ROOT" ]; then
        EXTRA_ROOT="$1"
      fi
      shift
      ;;
  esac
done

# ── Resolve root ────────────────────────────────────────────────────────
if [ -n "$EXTRA_ROOT" ]; then
  root="$EXTRA_ROOT"
elif [ -n "$WORKFLOW_DEFINITIONS_PATH" ]; then
  root="$WORKFLOW_DEFINITIONS_PATH"
else
  echo "Error: no root directory specified. Set WORKFLOW_DEFINITIONS_PATH or pass a path argument." >&2
  exit 1
fi

# ── Validate root ───────────────────────────────────────────────────────
if [ -z "$root" ]; then
  echo "Error: root is empty." >&2
  exit 1
fi

case "$root" in
  /*) ;; # absolute — ok
  *)
    echo "Error: root must be an absolute path (starts with /), got: $root" >&2
    exit 1
    ;;
esac

if [ "$root" = "/" ]; then
  echo "Error: root must not be exactly /." >&2
  exit 1
fi

if [ ! -d "$root" ]; then
  echo "Error: root does not exist as a directory: $root" >&2
  exit 1
fi

# ── --require-mount check ───────────────────────────────────────────────
if [ "$REQUIRE_MOUNT" = "1" ] && [ -f /proc/self/mountinfo ]; then
  if ! grep -q "${root} " /proc/self/mountinfo; then
    echo "Error: $root is not a real mountpoint (not found in /proc/self/mountinfo)." >&2
    exit 1
  fi
fi

# ── Mode dispatch ───────────────────────────────────────────────────────
case "$MODE" in
  inventory)
    echo "=== Workflow Definitions Inventory ==="
    echo "Root: $root"
    echo ""
    for d in $CLEAR_DIRS; do
      if [ -d "$root/$d" ]; then
        count=$(find "$root/$d" -maxdepth 1 -type f -name '*.yaml' | wc -l)
        echo "[CLEAR] $d — will clear $count depth-1 *.yaml file(s) (directory itself preserved)"
      else
        echo "[CLEAR] $d — directory absent, nothing to clear"
      fi
    done
    echo ""
    for d in $PRESERVE_DIRS; do
      echo "[PRESERVE] $d — untouched"
    done
    echo "[PRESERVE] workflow-workspaces — untouched"
    echo "[PRESERVE] git-mcp-workspaces — untouched"
    echo "[PRESERVE] Postgres data — untouched"
    echo "[PRESERVE] Keycloak data — untouched"
    ;;

  clear)
    for d in $CLEAR_DIRS; do
      if [ -d "$root/$d" ]; then
        find "$root/$d" -maxdepth 1 -type f -name '*.yaml' -exec rm -f {} +
      fi
    done
    ;;

  *)
    echo "Error: mode must be 'inventory' or 'clear', got: $MODE" >&2
    exit 1
    ;;
esac

exit 0
