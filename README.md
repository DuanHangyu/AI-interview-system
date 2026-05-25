# AI Interview System

AI 面试系统，包含后端 `defense-assessment` 和前端 `defense-system`。

## Backend

```bash
cp .env.example .env.local
# Fill .env.local with local secrets, then:
./scripts/start-backend.sh
```

## Frontend

```bash
npm --prefix defense-system install
./scripts/start-frontend.sh
```

默认前端本地地址：`http://localhost:3004/`。

## Local Restart

```bash
./scripts/restart-local.sh
```

本地密钥统一放在根目录 `.env.local`，该文件已被 Git 忽略。
