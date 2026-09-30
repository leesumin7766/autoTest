$ErrorActionPreference = "Stop"

cd C:\Users\tta\autoTest

# 1. ai-service
mkdir ai-service -Force | Out-Null
@"
fastapi
uvicorn[standard]
unstructured
pymupdf
langchain
langchain-community
pgvector
psycopg2-binary
python-multipart
typesafe-sdk
python-dotenv
"@ | Set-Content ai-service/requirements.txt -Encoding utf8

@"
from fastapi import FastAPI
app = FastAPI()
@app.get("/")
def health():
    return {"status": "ai-service running", "python": "3.13"}
"@ | Set-Content ai-service/main.py -Encoding utf8

@"
FROM python:3.13-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8005
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8005", "--reload"]
"@ | Set-Content ai-service/Dockerfile -Encoding utf8

# 2. crawler-service
mkdir crawler-service -Force | Out-Null
@"
{
  "name": "crawler-service",
  "version": "0.1.0",
  "main": "index.js",
  "scripts": { "start": "node index.js" },
  "dependencies": { "playwright": "^1.44.0", "express": "^4.19.2" }
}
"@ | Set-Content crawler-service/package.json -Encoding utf8

@"
const express = require('express');
const { chromium } = require('playwright');
const app = express();
app.use(express.json());
app.get('/', (req,res)=> res.json({status:'crawler-service running'}));
app.post('/crawl', async (req,res)=>{
  const { url } = req.body;
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const apis = [];
  page.on('request', r => apis.push({method: r.method(), url: r.url()}));
  await page.goto(url, {waitUntil:'networkidle'});
  await browser.close();
  res.json({apis: apis.slice(0,50)});
});
app.listen(8006, ()=> console.log('crawler on 8006'));
"@ | Set-Content crawler-service/index.js -Encoding utf8

# 3. docker-compose.yml 패치
Copy-Item docker-compose.yml docker-compose.yml.bak -Force
(Get-Content docker-compose.yml) -replace 'postgres:16-alpine', 'pgvector/pgvector:pg16' | Set-Content docker-compose.yml -Encoding utf8
if ((Get-Content docker-compose.yml) -notmatch 'minio:') {
  Add-Content docker-compose.yml @"

  minio:
    image: minio/minio
    container_name: autotest-minio-1
    command: server /data --console-address ":9001"
    ports: ["9000:9000","9001:9001"]
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin123
    volumes: [minio_data:/data]

  ai-service:
    build: ./ai-service
    container_name: autotest-ai-service-1
    ports: ["8005:8005"]
    environment: [TYPESAFE_API_KEY=`${TYPESAFE_API_KEY}]
    volumes: ["./ai-service:/app"]
    depends_on: [db]

  crawler-service:
    build: ./crawler-service
    container_name: autotest-crawler-1
    ports: ["8006:8006"]
    volumes: ["./crawler-service:/app"]
"@
}
if ((Get-Content docker-compose.yml) -notmatch 'minio_data:') {
  # minio_data is a volume, we might need to be careful with formatting in docker-compose.yml
  Add-Content docker-compose.yml "`nvolumes:`n  minio_data:"
}

# 4. .env
if (!(Test-Path .env)) { "TYPESAFE_API_KEY=" | Set-Content .env -Encoding utf8 }

# 5. 설치
cd crawler-service; npm install; npx playwright install chromium; cd ..
if (!(Test-Path frontend)) { npm create -y vite@latest frontend -- --template react-ts }
cd frontend; npm install; npm install axios react-router-dom; cd ..

Write-Host "`n=== 완료! ===" -ForegroundColor Green
