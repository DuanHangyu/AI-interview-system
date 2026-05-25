# AI Interview System

AI 面试系统，包含后端 `defense-assessment` 和前端 `defense-system`。

## Backend

```bash
cd defense-assessment
export DASHSCOPE_API_KEY=your_dashscope_key
export DB_PASSWORD=your_db_password
export REDIS_PASSWORD=your_redis_password
export ALIYUN_OSS_ACCESS_KEY_ID=your_oss_access_key_id
export ALIYUN_OSS_ACCESS_KEY_SECRET=your_oss_access_key_secret
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Frontend

```bash
cd defense-system
npm install
npm run dev
```

默认前端本地地址：`http://localhost:3004/`。
