FROM node:20-slim
WORKDIR /app
COPY package.json .
RUN npm install
RUN npx playwright install --with-deps chromium
COPY . .
EXPOSE 8006
CMD ["node", "index.js"]
