# One-shot TMDB importer image (§12). Built and run by the demo-importer Compose
# service after the app is healthy. It talks to People gRPC, Catalogue GraphQL,
# and the artwork HTTP endpoint — never the databases directly.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY tsconfig.json ./
COPY src ./src
COPY proto ./proto
RUN npm run build

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev
COPY --from=build /app/dist ./dist
COPY proto ./proto
# The manifest is mounted/copied at run time from the repo (demo/tmdb-movie-ids.txt).
# Default location inside the container; overridden by TMDB_MANIFEST_PATH.
ENV TMDB_MANIFEST_PATH=/app/manifest/tmdb-movie-ids.txt
ENTRYPOINT ["node", "dist/main.js"]
