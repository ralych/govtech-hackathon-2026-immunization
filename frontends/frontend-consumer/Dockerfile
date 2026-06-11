# Stage 1: Build the Expo Web App
FROM node:22-alpine AS builder

WORKDIR /app

# Copy dependency specifications and lockfile
COPY package*.json ./

# Install dependencies (utilizing Docker layer caching)
RUN npm ci

# Copy the rest of the application files
COPY . .

# Export/build the Expo project for the web platform
RUN npx expo export --platform web

# Stage 2: Serve using lightweight Nginx
FROM nginx:alpine

# Copy the static production build from the builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
