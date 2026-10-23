// Agent-authored, source-grounded regeneration through the EXTERNAL JSON contract.
// This is not a Qwen/AUTO call and never assigns a business classification.
const fs=require('node:fs');
const dir='outputs/neoflow-menu-classification';
const input=JSON.parse(fs.readFileSync(`${dir}/input.json`));
const features=JSON.parse(fs.readFileSync(`${dir}/document-features.json`));
const catalog=input.evidenceCatalog;
const specs=[
 [6,null,'로그인','로그인'],[6,'Log out','로그아웃','로그아웃'],
 [7,null,'로그인 시도 제한','잠금'],[8,null,'초기 비밀번호 강제 변경','초기 비밀번호'],[9,null,'비밀번호 복잡도 검증','복잡도'],
 [10,null,'회원가입','회원가입'],[10,'CONFIGURATION/Users','가입 승인','가입 승인'],
 [11,'CONFIGURATION/Users','사용자 조회|사용자 생성|사용자 수정|사용자 삭제|사용자 검색|사용자 역할 변경|사용자 상태 변경','사용자 관리'],
 [12,'CONFIGURATION/Users','역할별 접근 권한 확인','권한'],[13,'CONFIGURATION/Users','비밀번호 재설정','비밀번호 재설정'],
 [14,'CONFIGURATION/Settings/API Tokens','API 토큰 조회|API 토큰 발급|API 토큰 폐기','API 토큰'],
 [15,'Jobs','Job 파일 업로드','Job 업로드'],[16,'Jobs','Job 메타 등록','표시명'],
 [17,'Jobs','Job 목록 조회|Job 검색|Job 필터 프리셋 저장|Job 정렬|Job 보기 방식 전환','Job 목록'],
 [18,'Jobs','Job 상세 조회|Job 파일 다운로드|Job 변경 이력 조회','Job 상세'],[19,'Dashboard','작업 통계 조회','대시보드'],
 [20,'Jobs','Job 목록 CSV 내보내기|Job 목록 PDF 인쇄','CSV'],[21,'Jobs','Job 수정|Job 삭제','삭제'],
 [22,'Jobs','Job 활성 상태 변경|Job 스케줄 활성 상태 변경','활성'],
 [23,'Jobs','Job 일괄 선택|Job 일괄 실행|Job 일괄 취소|Job 일괄 활성화|Job 일괄 비활성화|Job 일괄 SLA 설정|Job 일괄 카테고리 설정|Job 일괄 삭제','일괄'],
 [24,'Jobs','실행 파라미터 설정|비밀 파라미터 마스킹','파라미터'],
 [25,'CONFIGURATION/Settings/Global Properties','공통 설정 조회|공통 설정 저장|활성 프로파일 지정|프로파일 복사|공통 속성 키 변경','공통 설정'],
 [26,'CONFIGURATION/Settings/General','공유 라이브러리 업로드|공유 라이브러리 삭제','공유 라이브러리'],
 [27,'Jobs','업무기준일 조회|업무기준일 수정','업무기준일'],[28,'Jobs','적재 유형 설정','적재 유형'],
 [29,null,'Studio 작업 메타 동기화|Studio 작업 아티팩트 업로드','동기화'],
 [30,'Jobs','Job 즉시 실행','즉시 실행'],[31,'Jobs','실행 이력 조회|실행 상세 조회','실행 이력'],
 [32,'OPERATIONS/Live','실행 취소|실행 일괄 취소','취소'],[33,'OPERATIONS/Live','실패 실행 재시도','재시도'],
 [34,'OPERATIONS/Logs','실시간 로그 조회|실행 로그 다운로드','실시간 로그'],[35,null,'동시 실행 수 제한','동시 실행'],
 [36,'Jobs','민감정보 마스킹','민감'],[37,'Schedules','작업 스케줄 설정|작업 스케줄 조회','Cron'],
 [38,'Schedules','스케줄 미리보기','미리보기'],[39,null,'클러스터 스케줄 중복 실행 방지','클러스터'],
 [40,'DAGs','DAG 생성|DAG 수정|DAG 삭제|DAG 목록 조회','DAG 생성'],[41,'DAGs','DAG 실행|DAG 실행 이력 조회','DAG 실행'],
 [42,'DAGs','DAG 스케줄 설정','스케줄'],[43,'DAGs','DAG 그래프 조회','그래프'],[44,'DAGs','DAG 트리거 규칙 설정','트리거'],[45,'DAGs','DAG 실행 취소','취소'],
 [46,'Dashboard','실행 현황 KPI 조회|실행 시간대별 통계 조회|실행 결과 분포 조회','대시보드'],
 [47,'Dashboard','SLA 위반 현황 조회','SLA'],[48,'Dashboard','시스템 자원 상태 조회|인프라 상태 조회','시스템 상태'],
 [49,'OPERATIONS/Live','실시간 실행 목록 조회|실행 일시정지|실행 재개|대기 실행 취소','실시간 실행'],
 [50,'OPERATIONS/Performance','성능 지표 조회','성능'],[51,'Jobs','SLA 목표 설정','SLA'],
 [52,'OPERATIONS/Logs','실행 로그 조회|실행 로그 검색|실행 로그 CSV 내보내기','로그'],
 [53,'OPERATIONS/Audit','감사 로그 조회|감사 로그 검색|감사 로그 변경 전후 비교|감사 로그 CSV 내보내기|감사 로그 PDF 인쇄','감사'],
 [54,null,'감사 로그 보존 정책 적용','보관'],[55,'OPERATIONS/Login History','로그인 기록 조회|로그인 기록 검색','로그인 기록'],
 [56,'CONFIGURATION/Alerts','경고 규칙 조회|활성 경고 조회','경고 규칙'],
 [57,'CONFIGURATION/Notifications','이벤트 알림 조회|이벤트 알림 필터|이벤트 알림 읽음 처리|이벤트 알림 삭제|모든 알림 읽음 처리','알림'],
 [58,'CONFIGURATION/Settings/Webhooks','웹훅 등록|웹훅 테스트 발송|웹훅 발송 이력 조회','웹훅'],
 [59,'CONFIGURATION/Settings/General','SMTP 설정|테스트 메일 발송','SMTP'],
 [60,null,'주간 리포트 예약 설정|주간 리포트 즉시 발송','주간'],
 [61,null,'실시간 토스트 알림 설정','토스트'],
 [62,'Lineage','리니지 그래프 조회|리니지 자산 추가|리니지 자산 편집|리니지 자산 삭제|리니지 연결 추가|리니지 연결 편집|리니지 연결 삭제','리니지'],
 [63,'Lineage','리니지 가져오기 검토|리니지 가져오기 적용|리니지 가져오기 거부','가져오기'],[64,'Lineage','리니지 재스캔','재스캔'],
 [65,'CONFIGURATION/Settings/General','시스템 설정 조회|시스템 설정 저장|보안 정책 설정|세션 유지 시간 설정','설정'],
 [66,'Settings','사용자 비밀번호 변경|본인 계정 삭제','비밀번호 변경'],
 [67,'CONFIGURATION/Settings/General','SMTP 설정|테스트 메일 발송','SMTP'],
 [68,'CONFIGURATION/Settings/General','보관 기간 설정|실행 이력 및 로그 즉시 정리','보관'],
 [69,'CONFIGURATION/Settings/General','라이선스 상태 조회|라이선스 업로드|머신 지문 복사','라이선스'],
 [70,null,'장애 진단 조회|장애 재분석|조치 코멘트 기록','장애 진단'],[71,null,'유사 장애 추천 조회','유사 장애'],
 [72,null,'헬스체크 조회','헬스'],[73,'한국어','언어 변경','한국어'],[74,'상단바/Search... ⌘ K','명령 팔레트 검색|명령 팔레트 이동','명령 팔레트'],
 [75,'Dark Mode','테마 변경','테마'],[76,null,'고가용성 구성','고가용성'],
];
function normalized(v){return v.replace(/\s+/g,'');}
const provenance=[];const rows=[];
for(const [excelRow,menu,names,manualTerm] of specs){
 const f=features.find(f=>f.excelRow===excelRow);if(!f)throw Error('Missing source row '+excelRow);
 let refs=catalog.filter(e=>e.kind==='DOCUMENT'&&e.role==='FUNCTION_LIST'&&normalized(e.quote).includes(normalized(f.name)));
 if(!refs.length){const needle=f.detail.split('\n')[0].replace(/^[•\s]+/,'').slice(0,32);refs=catalog.filter(e=>e.kind==='DOCUMENT'&&e.role==='FUNCTION_LIST'&&normalized(e.quote).includes(normalized(needle)));}
 if(!refs.length)throw Error('Cannot find function source '+excelRow+' '+f.name);
 const doc=refs[0];const manual=catalog.find(e=>e.kind==='DOCUMENT'&&e.role==='MANUAL'&&e.quote.includes(manualTerm));
 const anchor=menu?catalog.find(e=>e.kind==='MENU'&&e.webPath?.join('/')===menu):null;
 if(menu&&!anchor)throw Error('Unobserved path '+menu);
 for(const name of names.split('|')){
  const refs=[doc.refId,...(manual?[manual.refId]:[]),...(anchor?[anchor.refId]:[])];
  rows.push({menuPath:anchor?.webPath||[],functionName:name,menuDescription:`${name} 동작을 제공하는 기능`,location:anchor?.location||'UNKNOWN',
   evidenceRefIds:refs,reviewNotes:[`기능리스트 Sheet1 ${excelRow}행에서 재작성. 문서 기반 기능이며 실제 동작 성공을 뜻하지 않음.`,
    ...(anchor?[]:['화면 위치를 확정하지 못해 배치 검토 대상으로 보존.']),...(manual?[]:['매뉴얼 직접 인용 추가 검토 필요.'])]});
  provenance.push({functionName:name,sourceRow:excelRow,sourceFunction:f.name,menuPath:anchor?.webPath||[],evidenceRefIds:refs});
 }
}
for(const f of features)if(!provenance.some(p=>p.sourceRow===f.excelRow))throw Error('Uncovered original row '+f.excelRow);
const unique=new Map();
for(const row of rows){const key=JSON.stringify([row.menuPath,row.functionName]);const old=unique.get(key);if(old){old.evidenceRefIds=[...new Set([...old.evidenceRefIds,...row.evidenceRefIds])];old.reviewNotes=[...new Set([...old.reviewNotes,...row.reviewNotes])];}else unique.set(key,row);}
const result=[...unique.values()];
fs.writeFileSync(`${dir}/response.json`,JSON.stringify({requestId:input.requestId,rows:result},null,2));
fs.writeFileSync(`${dir}/provenance.json`,JSON.stringify({authoring:'Codex source-grounded draft; no Qwen/AUTO or separate external service call',originalFunctionRows:features.length,generatedFunctionRows:result.length,provenance},null,2));
console.log(JSON.stringify({rows:result.length,originalFunctionRows:features.length,anchored:result.filter(r=>r.menuPath.length).length,unassigned:result.filter(r=>!r.menuPath.length).length}));
