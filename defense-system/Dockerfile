# 1. 使用官方 Node 镜像构建前端资源
FROM node:18-alpine AS build-stage

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .

ENV VUE_APP_API_BASE_URL=https://intelligent-assessment-system.onrender.com
ENV VUE_APP_WEBSOCKET_BASE_URL=wss://intelligent-assessment-system.onrender.com
# ENV VUE_APP_API_BASE_URL=http://localhost:7050
# COPY .env.production .env

RUN npm run build

# 2. 用 nginx 镜像部署打包后的文件
FROM nginx:stable-alpine AS production-stage

COPY --from=build-stage /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
