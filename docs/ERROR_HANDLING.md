# 에러코드 관리

## 공통 규약

`common-lib/exception`의 `BaseErrorCode`는 `getCode/getMessage/getStatus`를 제공한다. `CommonException`은 모든 서비스의 `BaseErrorCode`를 받는다. 공통 `GlobalExceptionHandler`는 사용자용 메시지와 서버에서 생성한 추적 ID만 반환한다. 상세 예외는 서버 로그에 남기고 요청 본문·계정정보를 응답에 포함하지 않는다.

```json
{"code":"DOCUMENT_FORMAT_UNSUPPORTED","message":"허용되지 않은 파일 형식입니다. PDF, Excel, HWP, Word만 업로드 가능합니다.","traceId":"서버가 생성한 UUID"}
```

HTTP 번호는 응답 상태로 전달하며 메시지에 `400:`처럼 붙이지 않는다. enum은 HTTP 번호별 주석으로 묶는다. 응답의 `X-Trace-Id`와 본문의 `traceId`는 동일하다.

## 서비스별 관리

- 시험 관리 서비스: `exception/TestManagementErrorCode`, `TestManagementException`.
- 업로드 빈 파일·파일명·형식·용량·중복·교체 제한·저장 실패를 별도 코드로 구분한다.
- 기존 `ProductDescriptionException`은 공통 예외를 상속하는 호환 어댑터다. 기존 code와 HTTP 상태를 유지하되 동적으로 조합한 예외 메시지를 외부에 그대로 전달하지 않는다. 신규 업무 오류는 서비스 enum에 정의해 `TestManagementException`으로 발생시킨다.
- 인증·리포트·시험 실행 Java 서비스에는 공통 핸들러를 명시적으로 import했다. 현재 별도 업무 구현이 없는 서비스에 가상의 업무 에러코드를 만들지는 않는다.
- 게이트웨이는 WebFlux이므로 `GatewayExceptionHandler`에서 같은 응답 규약을 사용한다. 하위 서비스 연결 거부는 503, 응답 대기 초과는 504로 구분한다.
- Python AI·Node 크롤러의 내부 프로토콜이나 성공 응답에 담긴 비동기 작업 결과 형식은 이번 변경으로 일괄 변경하지 않는다.

## 프런트

모든 HTTP fetch 호출은 `apiFetch`를 사용한다. 서버에서 전달한 임의의 message, HTML 오류 페이지, 스택·SQL·경로를 그대로 표시하지 않는다. 공개된 서비스 코드의 안내와 HTTP 상태별 기본 안내만 사용한다. 서버 무응답은 `NETWORK_UNAVAILABLE`, 시간 초과는 `NETWORK_TIMEOUT`으로 구분하며 사용자 취소는 그대로 유지한다. 요청 자동 재전송은 하지 않는다.

서비스 코드를 추가·수정하면 `frontend/src/serviceErrorMessages.ts`도 동기화하고 `node scripts/verify-api-errors.mjs`로 일치를 검증한다. 검증은 실제 서비스 enum과 프런트 메시지를 대조한다.

## 검증

- 공통 MockMvc: 예상치 못한 오류, 업무 오류, 입력 오류, 잘못된 파라미터 타입, 업로드 용량 초과, 원본 예외 비노출 및 추적 ID.
- 게이트웨이: 연결 거부와 타임아웃의 상태·안전한 응답.
- 시험 관리: 기존 예외 코드·상태 호환, 공개 메시지, 문서 업로드 검증과 보존 로직, 격리 DB의 메뉴 업로드→TC 생성→시험 준비 연결.
- 프런트: 서비스 메시지 동기화, 서버 무응답·시간 초과·사용자 취소·HTML 오류·업로드 오류·정상 다운로드.
