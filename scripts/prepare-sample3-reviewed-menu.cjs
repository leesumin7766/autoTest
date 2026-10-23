// One-off, explicitly assisted review. This is NOT the app's AUTO generator.
const fs=require('node:fs');
const features=JSON.parse(fs.readFileSync('build/sample3-server-features.json','utf8'));
const input=JSON.parse(fs.readFileSync('build/sample3-menu-input.json','utf8'));
const documents=input.evidence.filter(s=>s.kind==='DOCUMENT');
const menus=input.evidence.filter(s=>s.kind==='MENU');
const paths={6:null,7:null,8:null,9:null,10:'CONFIGURATION/Users',11:'CONFIGURATION/Users',12:'CONFIGURATION/Users',13:'CONFIGURATION/Users',14:'CONFIGURATION/Settings/API Tokens',
15:'Jobs',16:'Jobs',17:'Jobs',18:'Jobs',19:'Jobs',20:'Jobs',21:'Jobs',22:'Jobs',23:'Jobs',24:'Jobs',25:'CONFIGURATION/Settings/Global Properties',26:'CONFIGURATION/Settings/General',27:'Jobs',28:'Jobs',29:null,
30:'Jobs',31:'Jobs',32:'OPERATIONS/Live',33:'OPERATIONS/Live',34:'OPERATIONS/Logs',35:null,36:'Jobs',37:'Schedules',38:'Schedules',39:null,
40:'DAGs',41:'DAGs',42:'DAGs',43:'DAGs',44:'DAGs',45:'DAGs',46:'Dashboard',47:'Dashboard',48:'Dashboard',49:'OPERATIONS/Live',50:'OPERATIONS/Performance',51:'Dashboard',52:'OPERATIONS/Logs',53:'OPERATIONS/Audit',54:null,55:'OPERATIONS/Login History',56:'CONFIGURATION/Alerts',57:'CONFIGURATION/Notifications',58:'CONFIGURATION/Settings/Webhooks',59:'CONFIGURATION/Settings/General',60:null,61:'CONFIGURATION/Notifications',62:'Lineage',63:'Lineage',64:'Lineage',65:'CONFIGURATION/Settings/General',66:'Settings',67:'CONFIGURATION/Settings/General',68:null,69:'CONFIGURATION/Settings/General',70:null,71:null,72:null,73:'한국어',74:'상단바/Search... ⌘ K',75:'Dark Mode',76:null};
const splits={6:[['로그인',null],['로그아웃','Log out']],10:[['회원가입',null],['가입 승인','CONFIGURATION/Users']],11:[['사용자 조회'],['사용자 생성'],['사용자 수정'],['사용자 삭제']],17:[['Job 목록 조회'],['Job 검색']],21:[['Job 수정'],['Job 삭제']],22:[['Job 활성 상태 변경'],['Job 스케줄 상태 변경']]};
const manualTerms={6:'로그인',7:'잠금',8:'초기 비밀번호',9:'복잡도',10:'회원가입',11:'사용자 관리',12:'권한',13:'비밀번호 재설정',14:'API 토큰',15:'Job 업로드',16:'표시명',17:'Job 목록',18:'Job 상세',19:'통계',20:'CSV',21:'삭제',22:'활성',23:'일괄',24:'파라미터',25:'공통 설정',26:'공유 라이브러리',27:'업무기준일',28:'적재 유형',29:'동기화',30:'즉시 실행',31:'실행 이력',32:'취소',33:'재시도',34:'실시간 로그',35:'동시 실행',36:'민감',37:'Cron',38:'미리보기',39:'클러스터',40:'DAG 생성',41:'DAG 실행',42:'스케줄',43:'그래프',44:'트리거 규칙',45:'취소',46:'대시보드',47:'SLA',48:'시스템 상태',49:'실시간 실행',50:'성능',51:'SLA',52:'로그',53:'감사',54:'보관',55:'로그인 기록',56:'경고 규칙',57:'알림',58:'웹훅',59:'SMTP',60:'주간',61:'토스트',62:'리니지',63:'가져오기',64:'재스캔',65:'설정',66:'설정',67:'SMTP',68:'보관',69:'라이선스',70:'장애 진단',71:'유사 장애',72:'헬스',73:'한국어',74:'명령 팔레트',75:'테마',76:'고가용성'};
function ref(source,term){const i=source.text.indexOf(term);return {sourceId:source.id,quote:source.text.slice(Math.max(0,i-35),Math.min(source.text.length,i+term.length+180)).trim()}}
function objectParticle(text){const code=text.charCodeAt(text.length-1)-0xAC00;return code>=0&&code<11172&&code%28===0?'를':'을'}
const rows=[];
for(const f of features){
 const source=documents.find(s=>s.role==='FUNCTION_LIST'&&s.text.includes(f.name));if(!source)throw Error('Missing original function '+f.excelRow);
 const term=manualTerms[f.excelRow];const manual=documents.filter(s=>s.role==='MANUAL'&&s.text.includes(term)).sort((a,b)=>Number(/\.{5}/.test(a.text))-Number(/\.{5}/.test(b.text)))[0];
 for(const [index,spec] of (splits[f.excelRow]||[[f.name]]).entries()){
  const [name,override]=spec;const requested=spec.length>1?override:paths[f.excelRow];
  const menu=requested?menus.find(s=>s.webPath?.join('/')===requested):null;if(requested&&!menu)throw Error('Unobserved menu '+requested);
  const first=(f.detail.split('\n').find(s=>s.replace(/[•·\s]/g,''))||f.name).replace(/^[•·\s]+/,'').slice(0,230);
  const description=splits[f.excelRow]?name:first.replace(/[。.]$/,'');
  const detail=`${description}${objectParticle(description)} 제공하는 기능`;
  const evidence=[ref(source,f.name),...(manual?[ref(manual,term)]:[]),...(menu?[{sourceId:menu.id,quote:menu.text.slice(0,240)}]:[])];
  rows.push({rowId:`review-server-${f.excelRow}-${index+1}`,menuPath:menu?.webPath||[],functionName:name,menuDescription:detail,location:menu?.location||'UNKNOWN',matchedMenuKeys:menu?[menu.menuKey]:[],evidence,
   reviewNotes:[`검토 보완본: 기능리스트 Sheet1 ${f.excelRow}행. AUTO 결과가 아닌 원문 기반 보완.`,menu?'관찰된 메뉴에 문서 기능을 배치한 검토 제안이며 실제 기능 수행 성공을 의미하지 않음.':'화면 위치 미확인: 배치 검토에 보존하며 TC 생성에서 제외.',...(manual?['매뉴얼 인용은 기능 용어의 참고 근거이며 정책 전체의 충족 증명이 아님.']:['매뉴얼의 직접 일치 구간은 추가 검토 필요.'])]});
 }
}
fs.writeFileSync('build/sample3-reviewed-rows.json',JSON.stringify(rows,null,2));
console.log(JSON.stringify({rows:rows.length,anchored:rows.filter(r=>r.matchedMenuKeys.length).length,unassigned:rows.filter(r=>!r.matchedMenuKeys.length).length,manualReferences:rows.filter(r=>r.evidence.length>1).length}));
