# 매뉴얼 기반 메뉴 트리 초안

## 목적

TC 생성 전에 기능의 상하위 경로를 먼저 검토할 수 있도록, 승인된 MANUAL 추출 텍스트의 번호형 제목을 후보 트리로 변환한다. 이 결과는 제품 UI를 확인해 확정하기 전까지 `DRAFT_NEEDS_REVIEW`다. 트리를 만들었다고 실제 화면 메뉴를 확인한 것으로 간주하지 않는다.

## 분리 규칙

- `featurePath`와 Excel의 1~5depth는 이름만 저장한다. `1.1.1.1` 같은 매뉴얼 절 번호를 기능 이름이나 depth 값으로 복사하지 않는다.
- `manualSection`, 인쇄된 매뉴얼 쪽, 원문 행은 근거 참조 메타데이터다.
- `필드 설명` 절은 상위 기능의 `supportingReferences`로 보낸다. 독립 메뉴/기능으로 만들지 않는다.
- 제품 정보 및 FAQ는 기능 트리에서 분리해 `nonFeatureReferences`로 보낸다.
- 자동 분류 결과에는 `NEEDS_HUMAN_REVIEW`를 유지한다. 실제 제품 UI와 대조하고 이름·계층·포함 여부를 사람이 승인해야 한다.
- 이 API는 초안만 반환한다. 저장·승인 UI가 연결되기 전까지 TC 생성의 선행 게이트로 쓰지 않는다.

## 사용자 흐름 및 API

사전 검증을 통과하면 프런트엔드가 메뉴 트리 초안을 자동 요청하고, 검토 화면을 보여준다. 검토자는 기능명을 수정하고 실제 제품 메뉴에 없는 후보를 제외하거나 후보의 상위 기능을 변경할 수 있다. 승인 시 TMS가 수정된 트리를 검증해 저장한다. 문서 digest가 바뀌면 저장된 트리는 만료되어 재생성·재승인이 필요하다.

- `GET /api/submissions/{id}/menu-tree`: 저장된 초안/승인 상태 조회
- `POST /api/submissions/{id}/menu-tree/draft`: MANUAL 텍스트로 초안 생성
- `PUT /api/submissions/{id}/menu-tree/approval`: 사용자가 편집한 트리 승인·저장
- TC 생성 API는 승인 트리가 없거나 현재 문서 digest와 다르면 `MENU_TREE_NOT_APPROVED` 또는 `MENU_TREE_STALE`로 거부한다.
- 승인된 기능 경로는 ai-service의 TC 생성 입력에 포함되고, 제품 동작 TC 경로는 서버와 ai-service에서 승인 경로와 일치하는지 확인한다.

내부 ai-service API:

`POST /api/v1/menu-trees/draft` (`X-Internal-Token` 인증)

```json
{
  "submissionId": "<submission UUID>",
  "manualFileId": "<MANUAL file UUID>",
  "extractedText": "<MANUAL extracted text>"
}
```

응답의 `nodes`는 기능 후보 계층, `nonFeatureReferences`는 제품 정보·FAQ, 각 기능의 `supportingReferences`는 연결된 필드 설명 등이다. `unattachedReferences`는 연결할 상위 기능을 찾지 못한 근거다.

## 현재 샘플 산출물

`artifacts/PrintChaser-메뉴트리-검토초안.xlsx`는 PrintChaser 관리자 매뉴얼에서 추출한 미승인 후보 50개와 메뉴 외 참조 19개를 담는다. 첫 시트의 depth 열은 경로 이름이며, 매뉴얼 절 번호와 쪽은 별도 근거 열에 있다. 이 과거 산출물은 UI에서 승인되지 않은 후보이며 새 제출의 초안·검토 흐름을 수동으로 시작하게 해준다.
