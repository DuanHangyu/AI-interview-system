#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.local}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE"
  echo "Create it from .env.example or ask Codex to regenerate local secrets."
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

BACKEND_PORT="${BACKEND_PORT:-8081}"
FRONTEND_PORT="${FRONTEND_PORT:-3004}"
RUN_DIR="$ROOT_DIR/.run"
mkdir -p "$RUN_DIR"

kill_port() {
  local port="$1"
  local pids
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    kill $pids 2>/dev/null || true
    sleep 1
  fi
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    kill -9 $pids 2>/dev/null || true
  fi
}

kill_port "$BACKEND_PORT"
kill_port "$FRONTEND_PORT"

nohup "$ROOT_DIR/scripts/start-backend.sh" > "$RUN_DIR/backend.log" 2>&1 &
BACKEND_PID="$!"

nohup "$ROOT_DIR/scripts/start-frontend.sh" > "$RUN_DIR/frontend.log" 2>&1 &
FRONTEND_PID="$!"

echo "Backend starting on http://localhost:$BACKEND_PORT (pid $BACKEND_PID, log .run/backend.log)"
echo "Frontend starting on http://localhost:$FRONTEND_PORT (pid $FRONTEND_PID, log .run/frontend.log)"
