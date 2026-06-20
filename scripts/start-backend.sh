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

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
elif [[ -d "/Users/duanhangyu/.homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home" ]]; then
  export JAVA_HOME="/Users/duanhangyu/.homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

"$ROOT_DIR/scripts/start-db-tunnel.sh"

if [[ -z "${DB_JDBC_URL:-}" ]]; then
  echo "DB_JDBC_URL is empty. Set it in $ENV_FILE."
  exit 1
fi
if [[ -z "${DB_USERNAME:-}" ]]; then
  echo "DB_USERNAME is empty. Set it in $ENV_FILE."
  exit 1
fi
if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "DB_PASSWORD is empty. Set it in $ENV_FILE."
  exit 1
fi

DB_HOST_PORT="${DB_JDBC_URL#jdbc:mysql://}"
DB_HOST_PORT="${DB_HOST_PORT%%/*}"
DB_HOST="${DB_HOST_PORT%:*}"
DB_PORT="${DB_HOST_PORT##*:}"
if [[ "$DB_HOST" == "$DB_PORT" ]]; then
  DB_PORT="3306"
fi

for attempt in {1..20}; do
  if nc -z -w 2 "$DB_HOST" "$DB_PORT" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" == "20" ]]; then
    echo "Database is not reachable at $DB_HOST:$DB_PORT."
    echo "Check DB_JDBC_URL and the SSH tunnel settings in $ENV_FILE."
    exit 1
  fi
  sleep 0.5
done

cd "$ROOT_DIR/defense-assessment"
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="${SPRING_PROFILE:-local}"
