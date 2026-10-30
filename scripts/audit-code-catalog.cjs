// Read-only repository inventory. Does not execute application code or read .env.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const git = (...args) => execFileSync('git', args, {cwd:root,encoding:'utf8'}).trim();
const tracked = git('ls-files','-z').split('\0').filter(Boolean).sort();
const sourceExtensions = new Set(['.java','.py','.js','.cjs','.mjs','.ts','.tsx','.sql','.ps1','.kts','.yml','.yaml','.properties','.html','.css','.json','.bat','.sh']);
function category(p) {
  if (p.includes('/node_modules/')) return '외부 의존성';
  if (/\/(?:__pycache__|build|bin)\//.test(p)) return '생성·컴파일 파일';
  if (/^(?:output|outputs|artifacts)\//.test(p)) return '기존 검증 산출물';
  if (/^sample\//.test(p)) return '시험 입력 샘플';
  if (/^docs\//.test(p)||/\.md$/i.test(p)) return '문서';
  if (/(?:\/src\/test\/|(?:^|\/)test_[^/]+\.py$|\.test\.js$|^scripts\/|\/verify_[^/]+\.py$)/.test(p)) return '검증·보조 코드';
  if (sourceExtensions.has(path.extname(p))) return '제품 코드·설정·템플릿';
  return '빌드 도구·자산·기타';
}
function symbols(p,text) {
  const found=[];
  text.split(/\r?\n/).forEach((line,i)=>{
    const patterns = p.endsWith('.java') ? [
      /\b(class|interface|enum|record)\s+(\w+)/g,
      /\b(?:public|protected|private|static)\s+(?:(?:static|final|synchronized|abstract|<[^>]+>)\s+)*(?:[\w<>,?\[\].]+\s+)+(\w+)\s*\(/g,
    ] : p.endsWith('.py') ? [/^\s*(?:async\s+)?(def|class)\s+(\w+)/g] : [
      /\b(?:async\s+)?function\s+(\w+)/g,
      /\bclass\s+(\w+)/g,
      /\b(?:const|let)\s+(\w+)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>/g,
    ];
    for(const re of patterns)for(const m of line.matchAll(re))found.push({line:i+1,name:m[2]||m[1]});
    for(const m of line.matchAll(/@(?:Get|Post|Put|Patch|Delete|Request)Mapping\(\s*['"]([^'"]+)['"]|@(?:app|router)\.(?:get|post|put|delete|patch)\(\s*['"]([^'"]+)['"]|app\.(?:get|post|put|delete|patch)\(\s*['"]([^'"]+)['"]/g))
      found.push({line:i+1,name:`HTTP ${m[1]||m[2]||m[3]}`});
  });
  return [...new Map(found.map(s=>[`${s.line}:${s.name}`,s])).values()];
}
const rows=tracked.map(p=>{
  const file=path.join(root,p); if(!fs.existsSync(file))return {path:p,category:category(p),missing:true};
  const buffer=fs.readFileSync(file),text=buffer.toString('utf8');
  const ownCode=['제품 코드·설정·템플릿','검증·보조 코드'].includes(category(p));
  return {path:p,category:category(p),bytes:buffer.length,sha256:crypto.createHash('sha256').update(buffer).digest('hex'),
    ...(ownCode?{lines:text.split(/\r?\n/).length,symbols:symbols(p,text)}:{})};
});
const counts={};for(const row of rows)counts[row.category]=(counts[row.category]||0)+1;
const out=path.join(root,'docs/code-audit');fs.mkdirSync(out,{recursive:true});
const roles = {
  'App.tsx':'현재 01~07 화면 진입, 문서 업로드·조회·전달 상태와 선택한 TC 세트 전달을 관리한다.',
  'MenuTcPanel.tsx':'메뉴 업로드·승인 메뉴 선택·기능구분 선택·템플릿 TC 생성·검색·상세·Excel/JSON 다운로드. TC 본문 저장용 편집 API는 호출하지 않는다.',
  'FunctionalMenuPanel.tsx':'외부 LLM 제품 프롬프트·JSON 가져오기·행 포함/배치/분리/편집·승인·근거와 캡처 조회.',
  'TestExecutionPanel.tsx':'TC 세트 선택·접속·메모·실행 범위·준비/시작/중단/이어 준비·진행·결과 상세.',
  'ExecutionReviewPanel.tsx':'종료한 실행의 사용자 판정·결함 상태·사유 저장, 선택 재시험 준비, 보고서 다운로드. TC 본문 편집과 다르다.',
  'ExecutionConnectionTest.tsx':'로그인 필요 선택과 계정·TLS 설정을 포함한 연결/로그인 검사 팝업. 성공한 설정을 저장한다.',
  'PlaywrightSetupPanel.tsx':'화면 수집 접속·자동 로그인 설정과 저장·조회. 원격 수집 패널을 연결한다.',
  'RemoteCrawlPanel.tsx':'로컬/원격 수집 작업·실행기 등록·진행·캡처·실패 사유·이어 수집 UI.',
  'CapturedMenuTreePanel.tsx':'수집 메뉴 구조를 화면에서 표시하는 보조 컴포넌트.',
  'SkippedMenuRetry.tsx':'미수집 메뉴를 SELF/SUBTREE 범위와 체크박스로 선택해 재수집을 요청한다.',
  'ExecutorStatus.tsx':'실행기 사용 가능/사용 중 버튼과 점유 작업 조회·중단 팝업.',
  'RunProgress.tsx':'단조 증가 소요 시간, 상태·진행 막대·지표 표시. 서버 수집/시험 동작 자체를 수행하지 않는다.',
  'useExecutionEvidence.ts':'실행 상세의 evidenceUrl을 요청하고 선택 변경 시 이전 요청을 취소한다.',
  'apiFetch.ts':'네트워크/HTTP 실패를 ApiError와 승인된 사용자 메시지로 변환한다.',
  'serviceErrorMessages.ts':'서버 오류 코드별 프런트 사용자 메시지 목록.',
  'browserCheckMessages.ts':'연결·로그인·수집 오류 코드의 사용자 메시지.',
  'tcClassification.ts':'사용자 선택·기존 구분 우선, 로그인/CRUD/주요기능 제안. Java 분류와 별도 구현이다.',
  'PreflightWarningDetails.tsx':'사전 점검 경고·추출 진단 상세 표시.',
  'TcGenerationPanel.tsx':'과거 LLM TC·메뉴트리 검토 UI. 현재 App.tsx는 이 컴포넌트를 import/render하지 않는다.',
  'ProductDescriptionPanel.tsx':'제품 설명 생성·중단·이력 UI. 현재 App.tsx는 이 컴포넌트를 import/render하지 않는다.',
  'MenuTcGenerator.java':'버전 관리 템플릿을 코드로 확장한다. 분류·원본 템플릿·안정 ID·내용 digest·초안 결과를 생성한다. 생성 시 LLM을 호출하지 않는다.',
  'MenuTcService.java':'소유권·승인·문서/메뉴 revision 확인, 기능/인증 입력 준비, TC 세트 저장·재사용·조회·다운로드.',
  'MenuTcWorkbook.java':'샘플 CLI의 다중 메뉴 시트 읽기와 TestCase 16열/실행 정보 20열/생성 정보 Excel 출력.',
  'MenuTcCli.java':'샘플 메뉴 workbook의 시트별 생성 결과를 파일로 출력하는 명령행 진입점.',
  'TcTemplateBinding.java':'템플릿을 기능명·동작 유형과 연결하고 적용 제외 후보·필요 정보·초기 실행 명세를 구성한다.',
  'TcProcedureWriter.java':'유형별 번호가 있는 시험 절차·입력·기대 출력과 실행 절차를 구성한다.',
  'TcExecutionSpecification.java':'엄격한 단순 조회만 v2-read로 전환한다. 대상·조회 전제·각 조건 근거를 미연결 상태로 생성하고 준비 검사를 한다.',
  'AuthenticationTcContext.java':'문서/수집/접속 설정으로 인증 필요·잠금 정책 후보를 구성하고 로그인 기능이 없으면 합성 로그인 기능을 추가한다.',
  'FunctionalMenuService.java':'문서·수집 증거 고정, 웹 구조·메뉴 작업·프롬프트·가져오기·검토 저장·승인·Excel·인증 문맥.',
  'FunctionalMenuValidator.java':'메뉴 경로·행·근거·기능명/설명 품질과 포함·배치 상태를 검사·정규화한다.',
  'MenuPlacementRecovery.java':'검증 가능한 관찰/문서 경로 근거로 배치 후보를 복원한다. 의미적 기능 정확성 확정과 다르다.',
  'SelfLlmMenuContract.java':'self-llm-v4 requestId·E번호 목록 생성, 응답 참조를 원문으로 복원, 중복/다른 요청 차단·참조 범위 보고.',
  'WebMenuStructure.java':'수집 근거로 관찰된 웹 메뉴 구조를 구성한다.',
  'FunctionalMenuExcel.java':'포함된 구조/기능 행을 메뉴트리 workbook으로 출력한다.',
  'UploadedMenuService.java':'UI 업로드 XLSX 첫 시트 또는 JSON rows를 승인된 업로드 메뉴 작업으로 저장한다. 화면 검증 완료로 취급하지 않는다.',
  'MenuTreeService.java':'기존 메뉴트리 승인/외부 응답 경로. 현재 functional-menu 경로와 구별한다.',
  'TcGenerationService.java':'기존 문서 기반 LLM TC 작업·진행·저장·중단·다운로드를 관리한다. 메뉴 템플릿 생성기와 별개다.',
  'TcExcelExporter.java':'기존 LLM TC 생성 결과 Excel 출력. MenuTcWorkbook과 출력 계약이 다르다.',
  'TestExecutionService.java':'선택 TC를 실행 스냅샷에 고정하고 사전 분류·이력·상태 전이·증거 조회·진행 갱신을 관리한다.',
  'TestExecutionWorker.java':'WAITING 작업 선점·TC별 AI 호출·저장 계정 전달·문서 발췌/메뉴 이동 힌트·결과 보호·별도 증거 저장.',
  'ExecutionReadinessService.java':'AI/브라우저 프로토콜·설치 모델·Jev 설정·점유·지원 명세 버전을 검사한다. 모델 품질 시험이나 추론 성공 검사는 아니다.',
  'ExecutionReviewService.java':'자동 판정 보존·사용자 판정 이력·결함 상태·선택 재시험/이어 준비·Markdown 초안.',
  'CapturedMenuTree.java':'수집된 메뉴 관찰을 표시용 계층 구조로 조립한다.',
  'CrawlAutomation.java':'수집 기본값과 URL/범위/자동 로그인/화면 수·깊이·시간 한도 검증.',
  'PlaywrightSetupService.java':'문서·사전 점검·소유권을 확인하고 접속 설정·암호화 계정·버전·대상별 TLS 예외를 관리한다.',
  'PlaywrightCredentialCipher.java':'저장 비밀번호의 암복호화와 키 파일 관리.',
  'RemoteCrawlService.java':'실행기 등록·작업 발행/선점/lease·수집 이벤트·페이지 보존·중단·SELF/SUBTREE 이어 수집.',
  'LocalCrawlDispatcher.java':'로컬 작업을 crawler API로 전달하고 기존 수집 작업 채널과 연결한다.',
  'CrawlAgentWire.java':'원격 에이전트 채널 데이터의 암호화/인증 처리.',
  'SubmissionService.java':'파일 형식·크기 검사, S3 원본 저장·파싱·역할별 추가/실패 교체·실패 정리.',
  'SubmissionPersistenceService.java':'세트/문서 DB 트랜잭션·필수 2역할·교체 조건·전달 claim과 45초 lease.',
  'AiDocumentDeliveryService.java':'문서 접수 요청·Jev 결과 수신·digest 저장·전달 성공/실패/차단·재시도 상태.',
  'DocumentDigest.java':'문서 입력 버전 동일성 확인용 digest.',
  'FileTypeResolver.java':'파일 확장자·MIME 기반 형식 확인.',
  'S3CleanupOutboxWorker.java':'삭제 예약 원본을 참조 여부 확인 후 비동기로 정리한다.',
  'FileStoragePort.java':'원본 저장/조회/삭제의 애플리케이션 포트.',
  'S3FileStorageAdapter.java':'AWS SDK S3Client로 원본·생성 파일 저장/조회/삭제.',
  'S3Config.java':'S3 endpoint·인증·path-style 클라이언트 설정.',
  'DocumentFileParserFactory.java':'등록된 FileParser 중 문서 형식을 지원하는 구현을 선택한다.',
  'PdfFileParser.java':'PDF 텍스트 추출. OCR 수행 엔진으로 간주하지 않는다.',
  'ExcelFileParser.java':'XLS/XLSX의 시트·셀 텍스트를 추출한다.',
  'DocxFileParser.java':'DOC와 DOCX 텍스트를 POI extractor로 읽는다.',
  'HwpFileParser.java':'HWP 텍스트를 hwplib로 읽는다.',
  'HwpxFileParser.java':'HWPX 문서 텍스트를 읽는다.',
  'BaseErrorCode.java':'서비스 오류 코드·메시지·HTTP 상태 인터페이스.',
  'CommonErrorCode.java':'HTTP 상태별 공통 오류 코드.',
  'CommonException.java':'BaseErrorCode를 보관하는 공통 예외.',
  'ErrorResponse.java':'code/message/traceId 오류 응답.',
  'TestManagementErrorCode.java':'TMS 업무별 오류 enum, HTTP 번호별 구분.',
  'TestManagementException.java':'TMS 오류를 공통 핸들러와 연결하는 예외.',
  'GatewayExceptionHandler.java':'Reactive Gateway의 오류 응답·traceId 처리.',
  'ContainerDnsConfiguration.java':'컨테이너 재생성 이후 Gateway DNS 연결을 위한 설정.',
  'test_execution.py':'실행 API·Qwen 단일 조작 계획·단순 조회/정상 로그인 코드 경로·증거 수집·Jev 판정·안전 검사.',
  'execution_contract.py':'원문 기대 조건 분리·조건 보정·미지원 사전 입력·수행 여부·계획용 관찰 이력.',
  'execution_evidence.py':'조건별 실제 증거·원문 범위·입력 연결·저장 재확인·P/F 보호 검사.',
  'execution_profiles.py':'조회/상호작용 구분·대상 도달·코드 메뉴 이동·반복 조작 키·정형 결과 비교.',
  'execution_selection.py':'명시적 동작 불일치 후보를 Qwen으로 검토한다. EXCLUDED를 N/A 확정으로 바꾸지 않는다.',
  'execution_specification.py':'v2-read의 원본 digest·경로·대상 근거·모든 단계/조건 연결을 검증하고 준비된 조회 경로를 실행기에 제공한다.',
  'login_execution.py':'저장 계정 정상 로그인 결정을 Qwen에 요청하고 입력·제출·인증 기록을 확인한다.',
  'initial_entry.py':'최초 화면 복구에 RESET_SESSION/STOP 결정만 요청한다.',
  'preflight.py':'결정적 텍스트 검사·제품/문서 역할 Jev 평가·경고/차단·동시 요청 한도.',
  'preflight_store.py':'사전 점검 결과 DB claim·lease·중복 요청 방지. 이 저장소에는 문서 원문을 보관하지 않는다.',
  'functional_menu.py':'과거/내부 자동 메뉴 생성·진행·checkpoint·prompt 경로. 현재 UI의 외부 LLM 방식과 구별한다.',
  'menu_tree.py':'기존 TC 생성 경로의 메뉴트리 계약·외부 응답/진행 기능.',
  'web_menu.py':'관찰된 웹 구조를 자동 메뉴 생성 입력/출력 처리에 사용한다.',
  'tc_generation.py':'기존 문서 기반 LLM TC 생성·청크·근거·스키마·진행 처리. 현재 메뉴 템플릿 생성 API가 호출하는 코드가 아니다.',
  'engine.js':'수집 공통 URL 범위·위험 동작 제한·폼 로그인, walker를 통한 실제 수집 진입.',
  'walker.js':'수집 queue·메뉴 이동 재현·페이지 상태/캡처·언어·시간/개수 한도·이어 수집.',
  'navigation.js':'메뉴 관찰·계층·탭/그룹 펼치기·메뉴 이동 조작.',
  'screen.js':'프레임 관찰·본문/요소·로딩 상태·화면 식별값.',
  'language.js':'한국어 우선 locale와 확인 가능한 언어 선택 UI, 이어 수집 언어 일관성.',
  'retry.js':'SELF/SUBTREE 선택과 이동/캡처/실패 항목 포함 범위를 계산한다.',
  'probe.js':'CONNECTION/LOGIN 검사와 인증서·HTTP·로그인 확인.',
  'protocol.js':'원격 실행기 HTTP 요청과 암호화 채널 처리.',
  'agent.js':'원격 설정 읽기·heartbeat·작업 claim·probe/crawl 실행·결과 이벤트·중단 신호.',
  'tc-execution.js':'TC별 Chromium 세션·관찰·활성 팝업 범위·단일/기본 조작·저장 계정 로그인·다운로드·범위 제한.',
  'setup.ps1':'과거 초기 생성 스크립트. 현재 소스를 덮어쓰고 MinIO 구성을 덧붙일 수 있으므로 현재 설치 절차로 사용하지 않는다.',
  'Main.java':'루트의 출력 예제 파일. Gradle 기본 src/main/java 소스 경로에 있지 않고 현재 제품 흐름에 연결되지 않는다.',
};
function role(row) {
  const p=row.path,b=path.basename(p);
  if(row.category==='검증·보조 코드')return `검증/보조 파일 ${b}. 선언 목록을 아래에서 확인한다. 파일 존재와 실제 통과 결과는 구별한다.`;
  if(roles[b])return roles[b];
  if(p==='ai-service/main.py')return 'FastAPI 앱·router 연결, 필수 2역할/선택 합의서 접수·중복 digest·사전 점검·AI 접수 DB 저장.';
  if(p==='crawler-service/index.js')return '로컬 브라우저 작업 API·수집/시험 공용 점유와 내부 토큰. 별도 과거 /crawl 라우트도 남아 있다.';
  if(p.endsWith('/Application.java'))return `${p.split('/')[0]} Spring Boot 기동 진입점. 업무 API 구현 여부는 Controller와 별도로 확인한다.`;
  if(b==='GlobalExceptionHandler.java')return p.startsWith('common-lib/')?'Servlet 서비스의 공통 예외·검증/HTTP 오류 변환·안전 응답과 traceId.':'공통 Servlet 예외 핸들러를 TMS 패키지에 연결한다.';
  if(p.includes('/presentation/controller/'))return `${b.replace('.java','')}의 HTTP 요청을 해당 애플리케이션 서비스에 연결한다. 경로·작업 메서드는 선언 목록에 기록했다.`;
  if(p.includes('/product_description/'))return `제품 설명 생성 패키지 ${b}: 구조화 문서·provider·템플릿·렌더링 중 아래 선언의 책임을 맡는다. 현재 메뉴 기반 TC 생성과 별개다.`;
  if(p.includes('/application/productdescription/'))return `제품 설명 작업 계층 ${b}: 작업 모델·저장·생성 요청·worker 중 아래 선언의 책임. 기본 신규 생성 비활성 경로다.`;
  if(p.includes('/domain/'))return `도메인 파일 ${b}: 아래 선언의 타입·값 검증·상태/포트 계약. HTTP 진입점이 아니며 실제 업무 연결은 서비스에서 확인한다.`;
  if(p.includes('/infrastructure/persistence/'))return `영속성 파일 ${b}: 엔티티·Spring Data/JDBC 저장 또는 도메인 저장소 어댑터. memory 하위는 영속 TC 세트 경로와 구별한다.`;
  if(p.endsWith('.sql'))return `스키마 마이그레이션 ${b}; 기존 파일은 적용 이력이며 새 업무 상태는 해당 테이블 사용 코드를 함께 확인한다.`;
  if(p.endsWith('.css'))return '화면 스타일·레이아웃·상태 색상/애니메이션 정의. 서버 수행 로직은 포함하지 않는다.';
  if(p.includes('/tc-templates/'))return '버전 관리 TC 템플릿 데이터. 생성기에서 선택한 버전과 과거 보존 버전을 구분한다.';
  if(p.includes('/menu_templates/'))return '내부 자동 메뉴 생성용 규칙·스키마·예시. 현재 self-llm-v4 공통 MD와 별개다.';
  if(p.includes('/doc_templates/'))return '제품 설명 템플릿의 내용·표현·버전 manifest 데이터.';
  if(/package(?:-lock)?\.json$/.test(p))return 'Node 패키지 선언/잠금 파일. 잠금 파일은 설치 버전 근거이며 실행 코드가 아니다.';
  if(/(?:\.kts|\.yml|\.yaml|\.properties|tsconfig.*\.json)$/.test(p))return '빌드·서비스·라우트·컴파일 설정 파일. 현재 실행 환경의 적용 여부와 소스 기본값은 별도로 확인한다.';
  if(p==='frontend/src/main.tsx')return 'React root에 App을 마운트하고 전역 스타일·폰트 파일을 불러온다.';
  if(p==='frontend/src/components/ui/tabs.tsx')return 'Radix Tabs를 감싼 화면 탭 컴포넌트.';
  if(b==='Install.ps1'||b==='Start-Agent.ps1')return 'Windows 원격 실행기의 설치 또는 기동 스크립트. 서버 TC 실행은 현재 이 원격 채널을 사용하지 않는다.';
  return `구성/보조 소스 ${b}. 아래 정적 선언 목록과 원문이 확인 근거이며 이 설명만으로 현재 UI 연결·완료를 단정하지 않는다.`;
}
fs.writeFileSync(path.join(out,'inventory.json'),JSON.stringify({commit:git('rev-parse','HEAD'),scope:'Git tracked paths at base HEAD; hashes are current working-tree bytes at generation time. New audit tools/docs and untracked outputs are outside the base snapshot. Ignored local dependencies/secrets are not application source.',counts,files:rows},null,2)+'\n');
const escape=s=>String(s).replaceAll('|','\\|').replaceAll('\n',' ');
const index=['# 코드·설정 파일별 정적 탐색 목록','',
  '감사 시작 시 Git 추적 파일 전체는 inventory.json에 경로·분류·해시로 기록했다. 아래는 자체 코드·설정·템플릿과 검증 스크립트의 선언/HTTP 경로 탐색 목록이다. 정규식 탐색 결과이며 언어 문법 전체를 분석한 호출 그래프나 모든 행의 의미 검증을 뜻하지 않는다. 외부 라이브러리·산출물도 전체 인벤토리에 별도 분류해 누락 여부를 확인한다. .env와 인증키를 읽거나 복사하지 않는다.',''];
for(const row of rows.filter(r=>r.symbols)) {
  const relative='../../'+row.path;
  index.push(`## [${row.path}](${relative})`,'',`${row.category} · ${row.lines}줄 · ${row.bytes}바이트`,'',role(row),'');
  index.push(row.symbols.length?row.symbols.map(s=>`- ${s.line}행: \`${escape(s.name)}\``).join('\n'):'- 탐색 패턴에 해당하는 선언 없음. 설정·템플릿·스타일 또는 별도 문법의 파일은 원문과 모듈 설명을 함께 확인한다.','');
}
fs.writeFileSync(path.join(out,'FILE_INDEX.md'),index.join('\n')+'\n');
const packages=new Map();
for(const row of rows.filter(r=>r.category==='외부 의존성')) {
  const rest=row.path.split('/node_modules/')[1];
  if(!rest||rest.startsWith('.'))continue;
  const name=rest.startsWith('@')?rest.split('/').slice(0,2).join('/'):rest.split('/')[0];
  if(!packages.has(name)){
    const manifest=path.join(root,'crawler-service/node_modules',name,'package.json');
    const metadata=fs.existsSync(manifest)?JSON.parse(fs.readFileSync(manifest,'utf8')):{};
    packages.set(name,{name,version:metadata.version||'',description:metadata.description||'',files:0});
  }
  packages.get(name).files++;
}
const all=['# 전체 추적 파일별 분류','',
  '기준 커밋의 Git 추적 파일을 하나씩 기록한다. 자체 코드의 상세 선언은 FILE_INDEX.md, 바이너리·문서·의존성의 크기/해시는 inventory.json을 확인한다. 아래 분류 설명은 파일의 역할 범주이며 제3자 코드의 모든 알고리즘을 검증했다는 의미가 아니다.','',
  '| 경로 | 분류 | 역할 |','| --- | --- | --- |'];
for(const row of rows){
  let explanation=row.symbols?role(row):row.category==='문서'?'작성 당시의 계약·지침·보고 문서. 현재 구현과 버전/시점을 대조한다.':row.category==='생성·컴파일 파일'?'원본 코드에서 생성된 파일. 제품 원본의 별도 구현으로 해석하지 않는다.':row.category==='기존 검증 산출물'?'기존 시험/화면 검증의 결과·스크린샷·기록. 현재 전체 품질 보증이 아니다.':row.category==='시험 입력 샘플'?'사용자가 제공한 시험 입력 자료. 명령으로 실행하거나 원본을 수정하지 않는다.':'빌드 도구·정적 자산·이미지·시험 인증서 등. 해당 파일 원문/형식 기준으로 확인한다.';
  if(row.category==='외부 의존성'){
    const rest=row.path.split('/node_modules/')[1]||'';
    const name=rest.startsWith('@')?rest.split('/').slice(0,2).join('/'):rest.split('/')[0];
    const pkg=packages.get(name);
    explanation=pkg?`${pkg.name}@${pkg.version} 배포 파일. 패키지 역할: ${pkg.description}`:'Node 패키지 설치 도구의 실행 래퍼/잠금 메타데이터.';
  }
  const href=row.path.split('/').map(part=>encodeURIComponent(part).replace(/[()]/g,c=>'%'+c.charCodeAt(0).toString(16))).join('/');
  all.push(`| [${escape(row.path)}](../../${href}) | ${row.category} | ${escape(explanation)} |`);
}
fs.writeFileSync(path.join(out,'ALL_FILES.md'),all.join('\n')+'\n');
const deps=['# 추적된 외부 패키지','',
  'crawler-service/node_modules의 package.json에 기록된 이름·버전·설명을 사용한다. 현재 컨테이너 설치 버전이나 공급망 검증 결과는 아니다. frontend와 AI 의존성은 각 package-lock.json/requirements.txt가 별도 근거다.','',
  '| 패키지 | 버전 | 추적 파일 수 | package.json의 설명 |','| --- | --- | ---: | --- |'];
for(const pkg of [...packages.values()].sort((a,b)=>a.name.localeCompare(b.name)))deps.push(`| ${escape(pkg.name)} | ${pkg.version} | ${pkg.files} | ${escape(pkg.description)} |`);
fs.writeFileSync(path.join(out,'DEPENDENCIES.md'),deps.join('\n')+'\n');
console.log(JSON.stringify({commit:git('rev-parse','--short','HEAD'),tracked:rows.length,counts,indexed:rows.filter(r=>r.symbols).length},null,2));
