# AI Interview System

AI 面试系统，包含后端 `defense-assessment` 和前端 `defense-system`。

## Local Setup

本地启动依赖根目录 `.env.local`。这个文件包含 API Key、数据库密码、SSH 密码等本地密钥，已经被 Git 忽略，不能提交。

```bash
cp .env.example .env.local
```

填完 `.env.local` 后，推荐直接用一键重启脚本：

```bash
./scripts/restart-local.sh
```

默认地址：

- 前端：`http://localhost:3004/`
- 后端：`http://localhost:8081/`
- 前端接口代理：`http://localhost:3004/dev-api/*`

## Database

本项目本地开发使用 ECS 上的 MySQL，不要直接依赖本机 MySQL。当前数据库服务器是：

- ECS：`8.136.219.184`
- MySQL：服务器本机 `127.0.0.1:3306`
- 本地隧道：`127.0.0.1:13307 -> 8.136.219.184:3306`
- Database：`defense_test`
- User：`defense_test`

`.env.local` 里应使用本地隧道地址，而不是直接连公网 MySQL：

```bash
DB_JDBC_URL='jdbc:mysql://127.0.0.1:13307/defense_test?useUnicode=true&characterEncoding=UTF-8&useSSL=false&zeroDateTimeBehavior=convertToNull&useTimezone=true&serverTimezone=GMT%2B8&allowMultiQueries=true'
DB_USERNAME='defense_test'
DB_PASSWORD='<数据库密码，放在 .env.local，不要提交>'

DB_TUNNEL_ENABLED='true'
DB_TUNNEL_LOCAL_HOST='127.0.0.1'
DB_TUNNEL_LOCAL_PORT='13307'
DB_TUNNEL_REMOTE_HOST='127.0.0.1'
DB_TUNNEL_REMOTE_PORT='3306'
DB_TUNNEL_SSH_HOST='8.136.219.184'
DB_TUNNEL_SSH_USER='root'
DB_TUNNEL_SSH_PASSWORD='<ECS SSH 密码，放在 .env.local，不要提交>'
```

`scripts/start-backend.sh` 会先调用 `scripts/start-db-tunnel.sh`。如果 `13307` 已经被 SSH 隧道监听，它会直接复用；如果没有监听，会按 `.env.local` 自动拉起隧道。

### Database Health Check

后端启动前先检查隧道：

```bash
lsof -nP -iTCP:13307 -sTCP:LISTEN
nc -vz -w 3 127.0.0.1 13307
```

启动后检查登录接口是否已经进入业务层：

```bash
curl -sS -X POST http://localhost:3004/dev-api/login \
  -H 'Content-Type: application/json' \
  --data '{"account":"__probe__","password":"__probe__"}'
```

期望看到 `用户不存在`，这代表前端代理、后端、数据库都已经通了。如果看到数据库连接失败，按下面顺序排查。

## Common Database Problems

`系统异常` 或 `数据库连接失败，请检查本地数据库隧道或 DB_JDBC_URL 配置` 通常不是前端 `request.ts` 的问题，而是后端查询数据库时失败。

常见原因：

- `127.0.0.1:13307` 没有监听：没有启动 SSH 隧道。运行 `./scripts/start-db-tunnel.sh` 或直接 `./scripts/restart-local.sh`。
- `DB_JDBC_URL` 还在指向公网 `8.136.219.184:3306`：本地开发应指向 `127.0.0.1:13307`。
- `DB_PASSWORD` 为空或错误：MySQL 会报 `Access denied for user 'defense_test'@'localhost'`。
- `DB_TUNNEL_SSH_PASSWORD` 为空或错误：隧道无法建立。
- 后端在隧道修好前已经启动过：重启后端，让 Hikari 重新初始化连接池。

快速修复流程：

```bash
./scripts/restart-local.sh
lsof -nP -iTCP:13307 -sTCP:LISTEN
curl -sS -X POST http://localhost:3004/dev-api/login \
  -H 'Content-Type: application/json' \
  --data '{"account":"__probe__","password":"__probe__"}'
```

如果仍然失败，看后端日志：

```bash
tail -f .run/backend.log
```

日志判断：

- `Connection refused`：隧道没起来，或 `DB_JDBC_URL` 端口不对。
- `Access denied ... using password: NO`：`DB_PASSWORD` 没填。
- `Access denied ... using password: YES`：`DB_PASSWORD` 填错。
- `用户不存在`：数据库链路正常，是账号本身不存在。

## Backend

```bash
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
