#!/usr/bin/env bash
# Start the standalone local MySQL 8.4.3 used for development (replaces the
# remote SSH-tunnel DB when the tunnel is unavailable). No Homebrew needed.
#
# First-time setup (already done once):
#   MySQL 8.4.3 prebuilt tarball extracted to ~/.local/mysql-8.4.3
#   data dir initialized with: mysqld --initialize-insecure
#   database defense_test + user defense_test/defense123 created
#
# Schema/seed (re-applyable):
#   scripts/local-db-schema.sql  +  scripts/local-db-seed.sql
set -euo pipefail

BASE="$HOME/.local/mysql-8.4.3"
DATA="$HOME/.local/mysql-data"
SOCK="/tmp/mysql-local.sock"

if [ ! -x "$BASE/bin/mysqld" ]; then
  echo "MySQL not found at $BASE"
  echo "Download mysql-8.4.3-macos14-arm64.tar.gz from dev.mysql.com and extract to $BASE"
  exit 1
fi

# Already running?
if "$BASE/bin/mysqladmin" --socket="$SOCK" -u root ping >/dev/null 2>&1; then
  echo "MySQL already running (socket $SOCK, port 3306)."
  exit 0
fi

echo "Starting local MySQL 8.4.3 on port 3306..."
"$BASE/bin/mysqld" \
  --basedir="$BASE" \
  --datadir="$DATA" \
  --socket="$SOCK" \
  --port=3306 \
  --pid-file="$DATA/mysqld.pid" \
  --mysqlx=OFF \
  --mysql-native-password=ON &

# Wait until it accepts connections
for i in $(seq 1 40); do
  if "$BASE/bin/mysql" --socket="$SOCK" -u root -e "SELECT 1" >/dev/null 2>&1; then
    echo "MySQL ready. App user: defense_test / defense123, db: defense_test"
    exit 0
  fi
  sleep 1
done
echo "MySQL did not become ready in time." >&2
exit 1
