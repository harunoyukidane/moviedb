# Concurrency/stress load-test image. Built and run by the load-test Compose
# service against the already-running app. Talks only to Catalogue GraphQL and
# the Catalogue/People artwork HTTP endpoints — never the databases directly.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev
COPY --from=build /app/dist ./dist
ENTRYPOINT ["node", "dist/main.js"]
