FROM node:24.21.0-slim
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
RUN npx playwright install --with-deps chromium
COPY . .
EXPOSE 8006
CMD ["node", "index.js"]
