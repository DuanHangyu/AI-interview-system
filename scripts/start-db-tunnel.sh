#!/usr/bin/env bash
set -euo pipefail

if [[ "${DB_TUNNEL_ENABLED:-false}" != "true" ]]; then
  exit 0
fi

LOCAL_HOST="${DB_TUNNEL_LOCAL_HOST:-127.0.0.1}"
LOCAL_PORT="${DB_TUNNEL_LOCAL_PORT:-13307}"
REMOTE_HOST="${DB_TUNNEL_REMOTE_HOST:-127.0.0.1}"
REMOTE_PORT="${DB_TUNNEL_REMOTE_PORT:-3306}"
SSH_HOST="${DB_TUNNEL_SSH_HOST:-}"
SSH_USER="${DB_TUNNEL_SSH_USER:-root}"

if [[ -z "$SSH_HOST" ]]; then
  echo "DB_TUNNEL_ENABLED=true but DB_TUNNEL_SSH_HOST is empty"
  exit 1
fi

if lsof -nP -iTCP:"$LOCAL_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  exit 0
fi

if [[ -n "${DB_TUNNEL_SSH_PASSWORD:-}" ]]; then
  if ! command -v sshpass >/dev/null 2>&1; then
    echo "sshpass is required when DB_TUNNEL_SSH_PASSWORD is set"
    exit 1
  fi
  SSHPASS="$DB_TUNNEL_SSH_PASSWORD" sshpass -e ssh \
    -fN \
    -o StrictHostKeyChecking=accept-new \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -o ExitOnForwardFailure=yes \
    -L "$LOCAL_HOST:$LOCAL_PORT:$REMOTE_HOST:$REMOTE_PORT" \
    "$SSH_USER@$SSH_HOST"
else
  ssh \
    -fN \
    -o StrictHostKeyChecking=accept-new \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    -o ExitOnForwardFailure=yes \
    -L "$LOCAL_HOST:$LOCAL_PORT:$REMOTE_HOST:$REMOTE_PORT" \
    "$SSH_USER@$SSH_HOST"
fi
