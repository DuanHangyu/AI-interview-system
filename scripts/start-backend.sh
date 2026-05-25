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

cd "$ROOT_DIR/defense-assessment"
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="${SPRING_PROFILE:-local}"
