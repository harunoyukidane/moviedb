#!/usr/bin/env bash
#
# Reset the demo (§12.2). Stops the stack and removes ONLY this project's named
# Compose volumes (databases + artwork). Local demo data will be permanently
# removed. Windows: use scripts/reset-demo.ps1, or run this under WSL / Git Bash.
#
# Usage:
#   ./scripts/reset-demo.sh          # prompts for confirmation
#   ./scripts/reset-demo.sh --yes    # non-interactive
#
set -euo pipefail

cd "$(dirname "$0")/.."   # repo root

ASSUME_YES=0
for arg in "$@"; do
  case "$arg" in
    --yes|-y) ASSUME_YES=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

cat <<EOF
WARNING: this will remove this project's containers AND its named volumes:
  - catalogue-db-data, people-db-data      (all movies, people, credits)
  - catalogue-artwork-data, person-artwork-data  (all uploaded images)
Local demo data will be permanently deleted. Other Docker projects are untouched.
EOF

if [ "$ASSUME_YES" -ne 1 ]; then
  printf "Proceed? [y/N] "
  read -r reply
  case "$reply" in
    y|Y|yes|YES) ;;
    *) echo "Aborted."; exit 0 ;;
  esac
fi

# `down -v` removes only the volumes declared in THIS compose project.
echo "==> Removing this project's containers and named volumes…"
docker compose --profile seed down -v --remove-orphans
echo "==> Done. Run ./scripts/setup.sh to rebuild and reseed."
