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
