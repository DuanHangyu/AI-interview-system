#!/usr/bin/env bash
# 本地构建前后端产物并放入 deploy/app，之后把 deploy/ 和 scripts/ 上传到服务器执行 docker compose up -d
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
(cd "$ROOT/defense-assessment" && ./mvnw -q -B package -DskipTests)
(cd "$ROOT/defense-system" && npm ci --no-audit --no-fund && npx vue-cli-service build --mode staging)
mkdir -p "$ROOT/deploy/app"
cp "$ROOT"/defense-assessment/target/defense-assessment-system-*.jar "$ROOT/deploy/app/app.jar"
rm -rf "$ROOT/deploy/app/web" && cp -r "$ROOT/defense-system/dist" "$ROOT/deploy/app/web"
echo "built into $ROOT/deploy/app"
