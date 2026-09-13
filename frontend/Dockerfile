# Runtime image for the SvelteKit BFF (adapter-node). Like the backend images, the
# app is built on the HOST (npm run build) and copied in — this avoids running
# npm install inside the container, which is both faster and works where the
# container cannot reach the npm registry through a TLS-intercepting proxy.
#
# Build first:  (from frontend/) npm install && npm run build
FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
# Non-root (security baseline §14).
RUN addgroup -S app && adduser -S app -G app

# adapter-node output + the runtime dependencies it imports (graphql, graphql-request).
COPY package.json ./
COPY node_modules ./node_modules
COPY build ./build

USER app
EXPOSE 3000
# The SvelteKit node server reads HOST/PORT and the server-only env (CATALOGUE_*,
# PEOPLE_HTTP_URL). ORIGIN is set so form actions/cookies use the right host.
ENV HOST=0.0.0.0 PORT=3000
ENTRYPOINT ["node", "build/index.js"]
