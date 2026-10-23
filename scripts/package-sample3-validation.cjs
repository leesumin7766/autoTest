const fs=require('node:fs/promises');
const path=require('node:path');
const crypto=require('node:crypto');
const root=path.resolve('output/sample3-validation-20261022');
const dest=path.resolve('outputs/sample3-e2e-20261022');
const read=async name=>JSON.parse(await fs.readFile(path.join(root,name),'utf8'));
const headers={'X-Member-Id':'1'};
const digest=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const escaped=value=>String(value??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const cell=value=>String(value??'').replaceAll('|','/').replaceAll('\n',' ');
async function main(){
 const state=await read('workflow.json'),audit=await read('artifact-audit.json'),docs=await read('01-documents.json'),tc=await read('04-test-cases.json');
 const base=`http://127.0.0.1:8080/api/submissions/${state.submissionId}`;
 const response=await fetch(`${base}/executions?run=${state.executionRunId}`,{headers});if(!response.ok)throw Error('Execution unavailable');
 const view=await response.json(),run=view.run;
 if(['RUNNING','WAITING_EXECUTOR','PREPARED'].includes(run.status))throw Error('Execution is still active');
 await fs.writeFile(path.join(root,'05-execution.json'),JSON.stringify(view,null,2));
 await fs.mkdir(path.join(dest,'증거'),{recursive:true});
 const selected=run.snapshot.items;
 const performed=selected.filter(t=>t.executionStatus!=='NEEDS_CONTEXT');
 for(const group of run.snapshot.groupResults||[]){
  const r=await fetch('http://127.0.0.1:8080'+group.evidenceUrl,{headers});if(!r.ok)throw Error('Evidence fetch failed');const body=await r.json();
  for(const [index,evidence] of (body.evidence||[]).entries())if(evidence.screenshot){
   const name=`${group.caseId.slice(0,12)}-${index}.png`;
   await fs.writeFile(path.join(dest,'증거',name),Buffer.from(evidence.screenshot.replace(/^data:image\/png;base64,/,''),'base64'));
   evidence.screenshotFile=name;delete evidence.screenshot;
  }
  await fs.writeFile(path.join(dest,'증거',`${group.caseId.slice(0,12)}.json`),JSON.stringify(body,null,2));
 }
 const autoResponse=await fetch(`${base}/functional-menu?job=${state.autoMenuJobId}`,{headers});const auto=await autoResponse.json();
 await fs.writeFile(path.join(root,'03-AUTO-중간결과.json'),JSON.stringify(auto,null,2));
 const ap=auto.job.progress||auto.job.output.progress;
 const af=auto.job.output.rows.filter(r=>r.rowType!=='MENU_OBSERVATION');
 const docSummary={submissionId:state.submissionId,sources:state.sources,manualPages:82,documentStatus:docs.status,preflight:docs.aiDelivery.preflight};
 await fs.writeFile(path.join(dest,'01-자료검증.json'),JSON.stringify(docSummary,null,2));
 const crawl=await read('02-crawl.json');await fs.writeFile(path.join(dest,'02-화면수집.json'),JSON.stringify(crawl,null,2));
 for(const [source,name] of [['03-메뉴트리.xlsx','03-메뉴트리-검토보완본.xlsx'],['03-menu.json','03-메뉴트리-검토보완본.json'],['03-AUTO-중간결과.json','03-메뉴트리-AUTO-중단원본.json'],['04-TC.xlsx','04-TC.xlsx'],['04-test-cases.json','04-TC.json'],['05-시험수행결과.xlsx','05-시험수행결과.xlsx'],['artifact-audit.json','파일검증.json']])await fs.copyFile(path.join(root,source),path.join(dest,name));
 await fs.writeFile(path.join(dest,'05-시험수행결과.json'),JSON.stringify(view,null,2));
 const reportResponse=await fetch(`${base}/executions/${state.executionRunId}/report`,{headers});if(!reportResponse.ok)throw Error('Report download failed');await fs.writeFile(path.join(dest,'05-서버보고서.md'),await reportResponse.text());
 const reasons=performed.map(item=>`| ${cell(item.tc.tcId)} | ${cell(item.tc.featurePath.join(' > '))} | ${cell(item.executionStatus)} | ${cell(item.result||'미판정')} | ${cell(item.reason)} |`).join('\n');
 const report=`# NeoFlow Server 01~05 실행 검증 보고서

## 결론

01 등록·검증, 02 화면 수집과 이어 수집, 04 TC 생성·Excel 출력, 05 선택 실행·증거 저장 경로를 실제 서버에서 진행했다. **완전 자동 성공으로 평가하지 않는다.** 03 AUTO 모드는 ${ap.completed}/${ap.total} 구간 시점에 문서 기능 ${af.length}개가 모두 배치 미확인으로 남아 원본을 보존하고 중단했다. 이후 원문과 관찰 메뉴를 대조한 별도 검토 보완본으로 04~05를 이어갔다. 보완본은 앱 AUTO 생성 결과가 아니다.

생성 TC ${tc.set.output.caseCount}건 중 이번 실행은 72건을 선택했다. 조회 3건, 파일 필요 1건, 공통·문서 68건이다. **나머지 590건은 미선택이며 전체 제품 시험을 완료했다는 뜻이 아니다.** 변경 시험 허용 답변이 없어 데이터 생성·수정·삭제·Job 실행은 비허용으로 유지했다.

## 입력과 추적

- 대상: ${state.targetUrl}
- 문서: ${state.sources.map(s=>s.name).join(' / ')}
- 기능리스트: Server 71개, Studio 18개. 이번 보완본은 Server 범위만 사용했다.
- 매뉴얼: 82쪽, 문서 버전 1.0.3. 문서 표지의 대상 제품 버전 v1.0/화면 표시 1.0.0과 문서 버전은 다르므로 실제 서버 버전은 미확인으로 기록했다.
- 문서 세트: ${state.submissionId}
- 수집: ${state.crawlJobId}
- 보완 메뉴: ${state.menuJobId}
- TC 세트: ${state.tcSetId}
- 시험 실행: ${state.executionRunId}
- 원본 파일과 제출 파일의 SHA-256은 manifest.json에 있다. 로그인 비밀번호와 암호화된 자격 증명은 포함하지 않는다.

## 단계별 확인

| 단계 | 결과 | 정상으로 확인한 범위 | 남은 점 |
|---|---|---|---|
| 01 자료 등록 | READY_WITH_WARNINGS | 두 역할 등록·파싱·같은 제품 확인, 생성 gate 허용 | 합의서 없음. 매뉴얼 87,540자 중 12,000자만 Jev 사전 판정. 가독성 신뢰도 매뉴얼 0.56/기능리스트 0.74 |
| 02 화면 수집 | 이어 수집 후 COMPLETE | 첫 32건·고유 23화면·미확인 3건을 재수집해 저장 46건·고유 37화면·미확인 0건, 누적 412.211초 | 메뉴 링크 관찰과 대상 내부 화면 조작의 성공은 별개. 아래 탐색 품질 참조 |
| 03 메뉴트리 | 검토 보완 후 APPROVED | 포함 기능 64개·구조 6행, 배치 검토 15개 보존. 본문 70행, 6열 Excel | AUTO 원본은 CANCELED. 근거 VALID는 인용 일치이며 의미적 연결 PROPOSED를 보증하지 않음 |
| 04 TC 생성 | 662건 | 주요기능 63×9, 로그아웃의 로그인 분류 1×27, 공통 68. 고유 ID·기능 연결·JSON/Excel 662행 일치 | 포괄적 기대 결과와 미구체화 입력. 로그아웃에 로그인 템플릿이 제안되는 분류도 검토 필요 |
| 05 시험 실행 | ${run.status} | 선택 72건 처리: P ${run.passCount}, F ${run.failCount}, N/A ${run.naCount}, 미수행 ${run.notExecutedCount}, 검토 필요 ${run.reviewCount}, 실행 오류 ${run.errorCount}. 소요 ${Math.round(run.elapsedMs/1000)}초 | 미수행은 N/A나 제품 F로 치환하지 않음. 실제 조회 3건 상세는 다음 표 |

## 조회 실행 상세

| TC | 기능 | 수행 상태 | 최종 판정 | 사유 |
|---|---|---|---|---|
${reasons}

파일 업로드 1건은 시험 파일 공급 기능이 없어 미수행으로 남겼다. 공통 68건은 문서·환경·수동 수행 증거가 필요해 미수행이다. 파일/폴더 공급은 이전에 합의한 후속 범위다. 증거 JSON의 plans는 계획이며 실제 수행은 evidence의 BROWSER_ACTIONS 기록으로 확인해야 한다. 자동 제안과 최종 확정 판정도 구분했다.

이번 조회 3건은 모두 Jev 판정 전에 종료됐다. 따라서 이번 결과의 원인을 Jev의 판정 신뢰도나 의미 분류 품질로 돌릴 수 없다. 계획·탐색·조작·요청 정책 단계에서 막힌 결과다.

### 추가 원인 추적과 수정 후 확인

동일 크롤러 환경에서 별도 읽기 전용 진단을 수행했다. Job 검색은 MY_B1 조건으로 1행을 표시했고 GET /api/jobs가 200이었다. 로그인 기록의 Search는 GET /api/login-history와 통계 조회가 200이었고 20행을 표시했다. 이것은 직접 작성한 조회 진단이며 Qwen/Jev TC의 P 판정으로 대체하지 않는다.

로그 화면은 **POST /api/logs/filter**라는 조회 API를 사용했다. 읽기 전용 실행기의 POST 일괄 차단 때문에 이 요청이 막혀 No logs로 보였다. 제품 결함의 증거가 아니다. 사이트별·기능별 승인된 조회 POST 계약이 필요하며 이번에 모든 POST를 허용하지 않았다.

로그인 기록 TC는 All Statuses를 열었지만 role=option을 관찰하지 못해 같은 트리거를 다시 누르다 실패했다. 수집 대상에 option/menuitem/combobox/checkbox/radio/switch를 추가하고 일반 컨텍스트 메뉴를 탐색으로 오인하지 않게 수정했다. 실제 사이트에서 All Statuses·Success·Failure 세 선택지를 수집하고 선택 동작 COMPLETED, isNavigation=false, 차단 0건을 확인했다. 별도 증거 dropdown-postfix.json/png에 보존했다.

오류 경로가 대상 화면 관찰을 추가하기 전에 반환해 이전 대시보드 스크린샷이 남는 문제도 수정했다. Python 17건·브라우저 8건 회귀 시험을 통과했고 서버에 반영했다. 위 72건 결과는 수정 전 실행 원본이며 사후에 성공으로 고치지 않았다. 드롭다운 수정 후 로컬 Qwen의 전체 재실행 품질까지 검증한 것은 아니다.

## 파일 검증

- 메뉴트리 3시트, TC 3시트를 독립 라이브러리로 다시 열고 모든 TC 행/고유 ID/실행 정보 ID를 대조했다. 중복 ID·누락 연결 없음. 생성 TC 결과 셀 662개는 빈 셀이다.
- 원본 Excel의 셀 수식은 0개다. 프로그램이 생성한 값과 결함 작성 예시를 실제 결함 결과로 혼합하지 않았다.
- 모든 시트를 렌더링해 확인했다. 원본 파일의 구조·서식은 보존했다. 파일이 열린다는 것과 시험 명세가 충분하다는 것은 구별한다.
- 시험 결과 Excel은 원본 TC와 선택 실행을 고유 ID로 결합한 별도 검증용 내보내기다. 앱의 원본 TC Excel을 덮어쓰지 않았다. 662건 전체에서 미선택/미수행/미판정을 구별하고 요약 수식을 대조했다.
- 실제 05 화면에서 실패 TC의 상세를 열어 소유권 헤더가 있는 증거 API 200, 계획과 실제 스크린샷 표시, 화면 예외 없음도 확인했다.

## 우선 개선점

1. **메뉴 자동 생성 입력 계약**: 100개 문서 근거가 381개 호출 구간으로 다시 분할됐다. 현재 예산 조절은 메뉴 후보를 제거할 수 있다. 초반 ${af.length}개 문서 기능이 모두 UNASSIGNED였다. 짧은 참조 ID, 기능리스트의 행/제품 경계 보존, 매뉴얼 근거 검색, 후보가 빠질 때의 명시적 경고가 필요하다. 중단 후 체크포인트를 다음 생성에서 이어 쓰는 기능도 필요하다.
2. **수집 완료 의미와 실제 탐색 증명**: 이어 수집은 미확인 3건만 추가한 것이 아니라 14개 캡처를 추가했다. 현재 캡처에는 같은 Settings 화면이 사용자 메뉴/Log out 등의 서로 다른 탐색 경로에 붙은 사례가 있고, Jobs 내부 컨트롤 근거는 없었다. 메뉴 클릭 뒤 URL·선택 상태·제목 변화로 실제 대상 도달을 검증하고 실패/변화 없음으로 분리해야 한다. COMPLETE는 내부 모든 기능의 수집 보증이 아니다.
3. **실행 가능한 TC 계약**: 생성된 662건 모두 executionReadiness=NEEDS_CONTEXT인 초안이다. 63건의 첫 기능 시험은 기대 결과가 '기능이 목적에 맞게 동작함'이다. '환경·절차 구체화 필요'만으로는 정확한 시험 동작과 합격 기준이 부족하다. 기능별 구체 동작·선택 대상·입력/전제·관찰 기준·정리 절차를 문서와 연결해야 한다. 정적 조회 화면 검증과 버튼 조작 시험도 구분해야 한다. N/A 허용은 이 정보 부족을 해결하지 않는다.
4. **로컬 모델 입력/시간·반복 상태**: 실제 계획 요청은 약 15,552토큰으로 문맥 16,384에 근접했고 입력 처리만 약 305초가 걸렸다. 현재 계획 600초/TC 900초의 한도와 3회 이상의 계획 왕복이 충돌할 수 있다. 첫 Job 조회는 실제 Jobs 화면에서 검색·필터 대신 Jobs 메뉴만 세 번 눌렀다. 알림 수 등 동적 본문이 바뀌면 현재 반복 fingerprint가 달라져 반복 탐색을 놓칠 수 있다. 대상 도달 상태를 고정하고 반복 요소·행 문맥을 구조화하며 관련 문서만 검색하되 생략 범위를 표시해야 한다. 최초 실패 증거와 전체 판정 증거는 유지해야 한다. 단순 타임아웃 증가는 해결이 아니다.
5. **Excel 출력 품질**: TC 590행은 짧은 경로를 왼쪽부터 채워 기능명이 고정 5depth 열에 오지 않는다. 메뉴트리와 같은 열 규칙이 필요하다. 실행 정보의 긴 근거 JSON 때문에 행이 과도하게 높고, 병합된 상위 메뉴가 화면 밖에서 가운데 정렬돼 첫 화면에는 이름이 비어 보인다. 근거의 읽기용 표현, 원문 별도 연결, 헤더 고정·행 높이·병합 규칙을 개선해야 한다.
6. **범위·버전·분류**: 기능리스트의 Server/Studio 제품 구간을 업로드 단계에서 선택해야 한다. 로그인 전 화면과 관리자/조회자 권한을 별도 수집해야 한다. 문서 버전/제품 버전/화면 표시 버전을 구분하고, 로그인·로그아웃·조회·변경별 템플릿 적용 기준을 세분화해야 한다.
7. **검증 완료 기준**: API 성공·파일 생성·메뉴 클릭·Jev 응답은 각각의 경로 확인이다. 정상/결함을 넣은 실행 가능한 TC로 P/F 및 미판정 비율을 측정하고, 제품 수행 오류와 자동화 오류를 따로 집계해야 한다. 변경 시험은 시험 데이터 격리·전후 확인·정리를 갖춘 뒤 진행한다.
8. **조회 정책과 실패 증거**: POST /api/logs/filter처럼 조회도 POST일 수 있다. 단순 메서드 차단과 기능별 허용 계약을 구분해야 한다. 드롭다운 역할 관찰과 오류 직후 화면 보존은 이번에 수정했으며, 승인된 조회 요청 목록과 실제 Qwen 재검증은 후속으로 남는다.

## 이번에 반영한 보호와 남은 범위

메뉴 이동 직후 판정 방지, 설명형 기대 결과의 문자열 오인 방지, 명시적 종료, 비동기 화면 대기, 정적 progressbar 오인 수정, 같은 출처 iframe 조작, 사전 정보 미수행 구분, 증거 분리/소유권 상세 조회, 설정 버전 검사와 취소 브라우저 정리를 반영했다. 회귀 시험 통과가 위 실제 제품 수행 품질까지 보증하지 않는다. 사전 파일 공급, 변경 데이터 격리·정리, 전체 제품/권한 시험, 장시간 대량 부하는 완료된 범위가 아니다.
`;
 await fs.writeFile(path.join(dest,'00-검증보고서.md'),report);
 await fs.writeFile(path.join(dest,'00-검증보고서.html'),`<!doctype html><html lang="ko"><meta charset="utf-8"><title>NeoFlow Server 검증 보고서</title><style>body{max-width:1100px;margin:40px auto;padding:0 24px;font:15px/1.7 sans-serif;color:#172033}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}</style><pre>${escaped(report)}</pre></html>`);
 await fs.copyFile(path.join(root,'02-화면수집.png'),path.join(dest,'증거','02-화면수집.png'));
 await fs.copyFile(path.join(root,'05-실제결과상세.png'),path.join(dest,'증거','05-실제결과상세.png'));
 for(const name of ['reference-diagnostic.json','reference-1.png','reference-2.png','reference-3.png','dropdown-postfix.json','dropdown-postfix.png'])await fs.copyFile(path.join(root,name),path.join(dest,'증거',name));
 for(const name of ['preview-results-summary.png','preview-results-detail.png'])await fs.copyFile(path.join(root,name),path.join(dest,'증거',name));
 const files=[];
 async function walk(dir){for(const entry of await fs.readdir(dir,{withFileTypes:true})){const file=path.join(dir,entry.name);if(entry.isDirectory())await walk(file);else if(entry.name!=='manifest.json'){const bytes=await fs.readFile(file);files.push({file:path.relative(dest,file).replaceAll('\\','/'),bytes:bytes.length,sha256:digest(bytes)})}}}
 await walk(dest);
 const manifest={createdAt:new Date().toISOString(),...state,autoGenerationCompleted:false,assistedMenuReview:true,allGeneratedCasesExecuted:false,generatedCases:662,selectedCases:72,unselectedCases:590,result:{status:run.status,pass:run.passCount,fail:run.failCount,na:run.naCount,notExecuted:run.notExecutedCount,review:run.reviewCount,error:run.errorCount},files};
 await fs.writeFile(path.join(dest,'manifest.json'),JSON.stringify(manifest,null,2));
 await fs.writeFile(path.join(root,'final-report.md'),report);
 console.log(JSON.stringify({directory:dest,files:files.length,result:manifest.result}));
}
main().catch(error=>{console.error(error);process.exitCode=1});
