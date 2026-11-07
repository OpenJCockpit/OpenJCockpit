#!/usr/bin/env bash
# Builds the git-mcp-server image for a release. It is not published by release-images.yml, so every
# deployment builds it from the source of the release tag itself:
#   deploy/scripts/build-git-mcp-server.sh <release-tag> <image-ref> [--push]
# The build context is `git archive <release-tag>`, not the working tree, so the image matches the tag
# whatever is checked out (a rollback rebuilds the older release the same way). An image that already
# exists (locally, or in the registry with --push) is reused: release tags are immutable.
set -euo pipefail

TAG="${1:?release tag}"; IMAGE="${2:?image reference}"; PUSH="${3:-}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [ "$PUSH" = "--push" ]; then
  docker manifest inspect "$IMAGE" >/dev/null 2>&1 && { echo "git-mcp-server: $IMAGE already in the registry"; exit 0; }
else
  docker image inspect "$IMAGE" >/dev/null 2>&1 && { echo "git-mcp-server: $IMAGE already built"; exit 0; }
fi

SHA="$(git -C "$ROOT" rev-parse --verify --quiet "$TAG^{commit}")" \
  || { echo "git-mcp-server: tag '$TAG' not found in $ROOT (run: git fetch --tags)"; exit 1; }
echo "git-mcp-server: building $IMAGE from $TAG ($SHA)"
git -C "$ROOT" archive --format=tar "$SHA" \
  | docker build --file services/git-mcp-server/Dockerfile \
      --label org.opencontainers.image.revision="$SHA" \
      --label org.opencontainers.image.version="$TAG" \
      --tag "$IMAGE" -

if [ "$PUSH" = "--push" ]; then
  docker push "$IMAGE"
fi
