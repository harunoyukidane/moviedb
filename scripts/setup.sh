#!/usr/bin/env bash
#
# Demo bootstrap (§12.2, §12.4). Brings up the stack, waits on health checks (not
# sleeps), then runs the one-shot TMDB importer to seed a browsable catalogue.
#
# Windows: run this under WSL or Git Bash — it is a Docker-only workflow; there is
# no PowerShell port. Docker Desktop must be running.
#
# Usage:
#   ./scripts/setup.sh              # bring up + seed (needs TMDB_READ_TOKEN)
#   ./scripts/setup.sh --skip-seed  # bring up an empty, usable app; no seeding
#
set -euo pipefail

cd "$(dirname "$0")/.."   # repo root

SKIP_SEED=0
for arg in "$@"; do
  case "$arg" in
    --skip-seed) SKIP_SEED=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

# --- 1. validate tooling ---
if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker is not installed or not on PATH." >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: 'docker compose' (v2) is required." >&2
  exit 1
fi

# --- load .env if present (for TMDB_READ_TOKEN etc.) ---
if [ -f .env ]; then
  set -a; . ./.env; set +a
fi

# --- 2 & 3. build + start, waiting on health checks ---
echo "==> Starting stack (building images, waiting for health)…"
docker compose up -d --build --wait

CATALOGUE_PORT="${CATALOGUE_HTTP_PORT:-8080}"

# --- decide whether to seed ---
TOKEN="${TMDB_READ_TOKEN:-}"
if [ "$SKIP_SEED" -eq 1 ]; then
  echo "==> --skip-seed given: leaving the app empty."
elif [ -z "$TOKEN" ]; then
  # Never fake success (§12.2): explain exactly how to seed later.
  cat <<EOF

==> TMDB_READ_TOKEN is not set — starting an EMPTY but usable app.
    To seed the demo catalogue later:
      1. Create a TMDB v4 Read Access Token: https://www.themoviedb.org/settings/api
      2. Add it to .env:   TMDB_READ_TOKEN=your-token
      3. Re-run:           ./scripts/setup.sh
EOF
else
  echo "==> Seeding demo data via the one-shot importer…"
  # 4. run the one-shot importer (seed profile); it exits non-zero on failure.
  if TMDB_READ_TOKEN="$TOKEN" docker compose --profile seed run --rm --build demo-importer; then
    echo "==> Seed complete."
  else
    code=$?
    echo "WARNING: importer exited with code $code. The app is up; some items may not have imported." >&2
  fi
fi

# --- 5. print URLs, counts, and test commands ---
echo ""
echo "======================================================================"
echo " MovieDB is up."
echo "   Catalogue GraphQL:  http://localhost:${CATALOGUE_PORT}/graphql"
echo "   Catalogue health:   http://localhost:${CATALOGUE_PORT}/actuator/health"
echo ""
echo " Demo record counts (via GraphQL):"
COUNTS=$(curl -s -X POST "http://localhost:${CATALOGUE_PORT}/graphql" \
  -H 'content-type: application/json' \
  -d '{"query":"{ movies(page:{limit:1,offset:0}){ total } }"}' 2>/dev/null || true)
if [ -n "$COUNTS" ]; then
  echo "   $COUNTS"
else
  echo "   (could not query counts; the API may still be warming up)"
fi
echo ""
echo " Try it:"
echo "   curl -s -X POST http://localhost:${CATALOGUE_PORT}/graphql \\"
echo "     -H 'content-type: application/json' \\"
echo "     -d '{\"query\":\"{ movies(page:{limit:5,offset:0}){ total items{ title } } }\"}'"
echo "======================================================================"
