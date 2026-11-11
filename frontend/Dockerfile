FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY fix-permissions.sh /docker-entrypoint.d/40-fix-permissions.sh
RUN chmod +x /docker-entrypoint.d/40-fix-permissions.sh
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 5174
