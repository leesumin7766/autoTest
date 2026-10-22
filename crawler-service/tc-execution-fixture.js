// Isolated synthetic site for real Qwen + Jev verification; never points at a user product.
const express = require('express');
const { timingSafeEqual } = require('node:crypto');
const app = express();app.use(express.json({ limit: '8mb' }));
let busy=false;
const auth=(req,res,next)=>{const a=Buffer.from(process.env.PRODUCT_DESCRIPTION_INTERNAL_TOKEN||''),b=Buffer.from(req.get('X-Internal-Token')||'');if(!a.length||a.length!==b.length||!timingSafeEqual(a,b))return res.sendStatus(403);next();};
require('./tc-execution').register(app,auth,()=>{if(busy)return false;busy=true;return true;},()=>{busy=false;});
app.get('/fixture/:mode',(req,res)=>{
  const mode=req.params.mode;
  if(mode==='missing')return res.type('html').send('<meta charset="utf-8"><h1>파일 전송</h1><label>시험 파일<input type="file"></label><button>전송</button>');
  if(mode.endsWith('read'))return res.type('html').send(`<meta charset="utf-8"><h1>신청 상세 조회</h1><p id="result">선택 전</p><button onclick="document.getElementById('result').textContent='상태: ${mode==='goodread'?'승인':'거절'}'">상세 보기</button>`);
  res.type('html').send(`<!doctype html><meta charset="utf-8"><title>저장 기능 시험</title><h1>저장 기능 시험</h1><p>저장 버튼을 누르면 결과 문구를 확인할 수 있습니다.</p><p id="result">저장 전</p><button onclick="document.getElementById('result').textContent='${mode==='broken'?'저장 실패: 서버 오류':'저장 완료'}'">저장</button>`);
});
const server=app.listen(8017,'0.0.0.0',()=>console.log('Synthetic execution fixture ready on 8017'));
app.post('/stop',auth,(_,res)=>{res.json({stopped:true});server.close();});
setTimeout(()=>{server.close();process.exit(0);},600000).unref();
