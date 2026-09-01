# ─── Build stage ─────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install --silent
COPY . .
ARG VITE_API_BASE_URL=http://localhost:8080/api
ARG VITE_WS_BASE_URL=ws://localhost:8080/ws
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_WS_BASE_URL=$VITE_WS_BASE_URL
RUN npm run build

# ─── Serve with nginx ─────────────────────────────────────────
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
