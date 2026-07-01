FROM node:20-alpine

WORKDIR /app

COPY web-frontend/package*.json ./
RUN npm ci

COPY web-frontend/ ./

EXPOSE 4200

CMD ["npx", "ng", "serve", "--host", "0.0.0.0", "--port", "4200", "--proxy-config", "proxy.docker.conf.json"]
