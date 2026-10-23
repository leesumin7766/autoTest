package com.autotest.test_management_service.application.testcase;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Human-readable procedures; unresolved product facts remain explicit prerequisites. */
final class TcProcedureWriter {
    static final String VERSION="procedure-v1.1";
    static void write(ObjectNode tc, String name, String path, String group) {
        String rule=tc.path("ruleId").asText(), original=tc.path("templateOriginal").path("scenario").asText();
        String operation=tc.path("executionPlan").path("selection").path("requiredOperation").asText();
        if(Set.of("CRUD-01","FEATURE-01").contains(rule))operation=TcTemplateBinding.operation(name);
        String scenario=name+": "+(group.equals("LOGIN")?tc.path("scenario").asText().replaceFirst("(?s)^\\[.*?\\]\\n", ""):original);
        List<String> steps=new ArrayList<>(), checks=new ArrayList<>();
        String prerequisite="사전조건: 시험 계정·권한, 대상 식별값、입력값·비교할 기대 데이터와 화면 요소를 문서·수집 화면으로 확정한다.".replace('、', '·');
        steps.add("시험 계정으로 접속하여 "+path+" 화면으로 이동한다.");
        if(Set.of("COMMON","PRODUCT_DOCUMENT","USER_MANUAL").contains(group)) {
            prerequisite="사전조건: 대상 제품·버전, 해당 문서 및 시험 기준과 필요한 점검 도구를 준비한다.";
            steps.clear();steps.add("해당 제품·버전의 문서와 시험 대상 범위를 확인한다.");
            steps.add("다음 기준에 해당하는 문서 구간 또는 제품 증거를 확보한다: "+original);
            steps.add("원문 기준과 확보한 실제 증거를 항목별로 대조하고 위치·값을 기록한다.");
            checks.add(tc.path("expectedResult").asText());
            checks.add("기준별 실제값·문서 위치·도구 결과를 기록한다. 문서 또는 증거가 없으면 충족으로 판단하지 않는다.");
        } else if(group.equals("LOGIN")) {
            steps.set(0,"시험 접속 주소에서 인증 관련 대상 화면을 연다. 로그인 상태가 필요한 항목은 사전조건에 맞는 시험 세션을 준비한다.");
            login(tc,rule,steps,checks);
        } else if(rule.endsWith("-13")&&group.equals("CRUD")) {
            steps.add("등록 또는 수정 화면을 열고 시험할 필수 필드 하나를 비운다. 나머지는 유효한 시험값을 입력한다.");
            steps.add("저장 동작을 한 번 수행하고 필드 안내와 저장 결과를 확인한다.");
            steps.add("목록을 다시 조회하여 생성 여부 또는 기존 값 유지 여부를 확인한다.");
            checks.add("비운 필수 필드를 식별할 수 있는 입력 안내가 표시된다.");checks.add("새 데이터가 생성되지 않거나 수정 전 값이 유지된다.");
        } else if(Set.of("CRUD-10","CRUD-16").contains(rule)) {
            steps.add("삭제 전용 시험 데이터와 비교용 데이터를 식별하고 현재 값을 기록한다.");
            steps.add("대상 데이터의 삭제 동작을 선택하고 확인 안내의 대상 식별값을 확인한다.");
            steps.add("확인 창에서 취소한 뒤 목록을 새로 조회한다.");
            checks.add("삭제 확정 전에 대상과 삭제 의도를 식별할 수 있는 안내가 표시된다.");
            checks.add("취소 후 대상 데이터와 비교용 데이터가 기존 값으로 유지된다.");
        } else if(rule.equals("FEATURE-05")||rule.equals("CRUD-05")) {
            steps.add("시험 대상의 기존 상태와 값을 기록하고 취소 가능한 작업 화면을 연다.");
            steps.add("시험값을 입력한 뒤 저장·확정 전에 취소 동작을 선택한다.");
            steps.add("대상 화면으로 다시 이동하여 기존 상태와 값을 비교한다.");
            checks.add("취소한 작업이 저장·확정되지 않고 기존 값과 상태가 유지된다.");
        } else if(rule.equals("FEATURE-08")||rule.equals("CRUD-12")) {
            steps.add("입력 화면을 열고 문서에 명시된 필수 필드와 화면의 필수 표시를 대조한다.");
            steps.add("각 필수 필드를 한 번씩 비운 채 제출하여 해당 필드의 안내를 확인한다.");
            checks.add("문서의 필수 필드를 화면에서 식별할 수 있다.");
            checks.add("비운 필드에 입력 요청이 표시되고 미완성 데이터가 저장되지 않는다.");
        } else if(rule.equals("FEATURE-06")||rule.equals("FEATURE-07")) {
            steps.add("문서의 허용 확장자·크기·개수와 시험 파일을 준비하고 업로드 화면의 안내를 비교한다.");
            steps.add("허용 파일과 금지 파일을 각각 별도 조건으로 선택해 업로드를 한 번씩 수행한다. 실행 파일은 격리된 시험용 무해 파일만 사용한다.");
            checks.add("허용 조건의 파일은 지정된 결과로 처리되고 금지 조건의 파일은 식별 가능한 사유와 함께 거부된다.");
            checks.add("거부된 파일이 처리·실행되지 않으며 기존 데이터는 유지된다.");
        } else if(Set.of("FEATURE-09","CRUD-14").contains(rule)) {
            steps.add("문서·화면에서 필드별 허용 형식과 최소·최대 경계를 확인하고 경계 안·경계값·경계 밖 시험값을 준비한다.");
            steps.add("각 조건을 독립된 시험 상태에서 하나씩 입력하여 제출하고 안내와 저장 결과를 기록한다.");
            checks.add("문서의 입력 제약이 화면에 안내되고 허용 경계값은 지정된 결과로 처리된다.");checks.add("허용 범위 밖의 값은 해당 사유가 표시되며 저장되지 않는다.");
        } else if(Set.of("FEATURE-02","FEATURE-03","CRUD-02","CRUD-03").contains(rule)) {
            steps.add("문서에 정의된 성공·오류 조건별 시험값과 안내해야 할 내용을 준비한다.");
            steps.add("조건별 동작을 각각 한 번 수행하고 표시된 안내 문구와 연결된 대상·처리 결과를 기록한다.");
            checks.add("안내에서 대상·수행 동작·처리 결과 또는 오류 사유를 식별할 수 있고 실제 조건과 일치한다.");checks.add("실패한 동작을 성공으로 안내하거나 다른 대상의 결과를 표시하지 않는다.");
        } else if(Set.of("FEATURE-04","CRUD-04").contains(rule)) {
            steps.add("상태 표시를 제공하는 시험 작업을 한 번 시작하고 시작 전·진행 중·종료 후 표시를 기록한다.");
            steps.add("기록한 상태와 해당 작업의 실제 완료·실패 결과를 비교한다.");
            checks.add("화면 상태가 해당 작업의 실제 진행·완료·실패 결과와 일치한다.");checks.add("진행 상태가 최종 결과로 갱신되고 실패한 작업이 완료로 표시되지 않는다.");
        } else if(Set.of("CRUD-06","CRUD-07","CRUD-08","CRUD-09","CRUD-01","FEATURE-01").contains(rule)&&!operation.isBlank()) {
            scenario=name+": "+switch(operation){case "CREATE"->"유효한 시험값으로 등록한 결과가 재조회 후에도 유지되는지 확인";case "UPDATE"->"대상 값 수정 후 변경값이 재조회 후에도 유지되는지 확인";case "DELETE"->"선택한 대상만 삭제되고 다른 데이터는 유지되는지 확인";case "READ"->"조회 조건에 맞는 결과와 표시값이 기대 데이터와 일치하는지 확인";default->original;};
            switch(operation) {
                case "CREATE" -> {steps.add("등록 화면을 열고 필수 필드에 준비한 유효 시험값을 입력한다.");steps.add("등록·저장 동작을 한 번 수행한다.");steps.add("고유 식별값으로 재조회하고 상세 값을 입력값과 비교한다.");checks.add("고유 식별값에 해당하는 데이터가 한 건 생성되고 입력한 값과 일치한다.");}
                case "UPDATE" -> {steps.add("시험 대상과 비교용 데이터의 기존 값을 기록하고 대상 수정 화면을 연다.");steps.add("지정 필드만 준비한 변경값으로 입력하고 저장 동작을 한 번 수행한다.");steps.add("새로고침 후 같은 식별값으로 재조회하고 변경값을 비교한다.");checks.add("대상 필드가 변경값으로 표시되고 새로고침 후에도 유지된다.");}
                case "DELETE" -> {steps.add("삭제 전용 시험 대상과 비교용 데이터가 존재하는지 확인하고 식별값을 기록한다.");steps.add("대상의 삭제 동작을 선택하고 확인 안내에서 해당 대상을 확인한 뒤 한 번 확정한다.");steps.add("고유 식별값으로 새로 조회하여 대상과 비교용 데이터의 존재 여부를 확인한다.");checks.add("삭제한 대상이 재조회 결과에 존재하지 않는다.");}
                case "READ" -> {steps.add("사전에 확인한 시험 데이터와 조회 조건을 준비하고 조건을 입력·선택한다. 조건 입력이 없는 요약 화면은 기준 시점의 기대값을 준비한다.");steps.add("조회 동작 또는 화면 로딩 완료를 기다린다.");steps.add("표시된 항목·건수·값을 준비한 기대 데이터와 항목별로 비교한다.");checks.add("표시 항목·건수·값이 조회 조건과 기준 시점의 기대 데이터에 일치한다.");}
                default -> {steps.add("준비한 허용 시험 파일을 파일 선택 요소에 지정하고 업로드 동작을 한 번 수행한다.");steps.add("처리 완료 후 파일명·처리 결과·결과 데이터를 기대값과 비교한다.");checks.add("선택한 파일의 처리 결과와 결과 데이터가 준비한 기대값에 일치한다.");}
            }
            checks.add(operation.equals("READ")?"조회 전후 제품 데이터가 변경되지 않는다.":"대상 외 비교용 데이터는 변경되지 않는다. 성공 알림만으로 저장·삭제 성공을 확정하지 않는다.");
        } else {
            // Preserve each rule's distinct intent; do not guess a universal click/save flow.
            steps.add("해당 조건과 시험값을 준비한다: "+tc.path("templateOriginal").path("inputs").asText());
            steps.add("다음 시험 동작을 수행하고 단계별 화면·값을 기록한다: "+original);
            steps.add("화면의 표시값·메시지·상태와 관련 결과를 아래 기대 출력 및 원문 근거와 대조한다.");
            checks.add(rule.equals("FEATURE-01")?"[확인 필요] 이 기능의 결과 화면·표시 항목·변경 상태를 문서와 수집 근거에서 확정하고, 실제 결과를 각 기대값과 비교한다.":tc.path("expectedResult").asText());
            checks.add("해당 기준의 실제 표시값·상태를 기대값과 비교한 증거를 확보한다. 기대값이 미확정이면 준비 필요로 남긴다.");
        }
        tc.put("scenario",scenario).put("inputs",prerequisite+"\n"+number(steps)).put("expectedResult",number(checks));
        tc.put("procedureVersion",VERSION);
        var plan=(ObjectNode)tc.path("executionPlan");
        var array=plan.putArray("steps");steps.forEach(array::add);
        plan.put("prerequisites",prerequisite);
    }
    private static void login(ObjectNode tc,String rule,List<String> steps,List<String> checks) {
        switch(rule) {
            case "LOGIN-03" -> {steps.add("비밀번호 필드에 시험 문자열을 입력한다.");steps.add("기본 표시 상태를 확인하고 비밀번호 표시 전환 기능이 있다면 전환 전후를 비교한다.");checks.add("기본 상태에서 비밀번호 원문이 화면에 노출되지 않고 마스킹 문자로 표시된다.");}
            case "LOGIN-04" -> {steps.add("유효한 시험 계정의 아이디와 비밀번호를 각각 입력한다.");steps.add("로그인 동작을 한 번 수행한다.");steps.add("인증 후 화면과 해당 계정의 허용 메뉴를 확인한다.");checks.add("로그인 화면에서 인증 후 화면으로 이동하며 해당 역할의 메뉴가 표시된다.");checks.add("문서·설정으로 확인한 권한 밖의 화면 접근이 허용되지 않는다.");}
            case "LOGIN-05" -> {steps.add("잠금 임계치 미만의 새 시험 계정 상태에서 잘못된 아이디 또는 비밀번호를 입력한다.");steps.add("로그인 동작을 한 번 수행하고 안내와 인증 상태를 확인한다.");checks.add("인증 실패 안내가 표시되고 보호된 화면으로 이동하지 않는다.");checks.add("보호된 화면 접근 시 인증을 요구하며 인증된 세션이 생성되지 않는다.");}
            case "LOGIN-17", "LOGIN-18" -> {
                var policy=tc.path("authenticationPolicy");String count=policy.has("failedAttemptLimit")?policy.path("failedAttemptLimit").asText():"[문서·설정으로 확정할 실패 횟수]";
                steps.add("잠금 시험 전용 계정의 실패 횟수를 초기화하고 정책·복구 절차를 확인한다. 정책 미확정이면 시도를 시작하지 않는다.");
                steps.add("잘못된 비밀번호 입력 → 로그인 제출을 "+count+"회 반복하며 매회 결과를 기록한다.");
                steps.add("올바른 비밀번호로 로그인하여 접근 제한을 확인한다." );
                if(rule.equals("LOGIN-18"))steps.add("새 브라우저 세션에서도 같은 계정의 접근 제한을 확인한다.");
                steps.add("확인된 해제 절차로 복구하고 정상 로그인이 가능한지 확인한다.");
                checks.add("정책의 실패 횟수에서 잠금 안내가 표시되고 올바른 비밀번호로도 보호된 화면에 진입하지 못한다.");
                if(rule.equals("LOGIN-18"))checks.add("새 세션에서도 계정 제한이 유지된다.");
                checks.add("정책에 따른 해제 후 정상 로그인이 가능하다. 실패 입력마다 제출하며, 성공할 때까지 변경 동작을 재시도하지 않는다.");
            }
            case "LOGIN-19" -> {steps.add("유효한 시험 계정으로 로그인하고 보호된 화면을 연다.");steps.add("로그아웃 동작을 한 번 수행한다.");steps.add("기존 보호 화면 주소를 새로 열어 인증 요구 여부를 확인한다.");checks.add("로그인 화면 또는 문서에 명시된 비인증 화면으로 이동한다.");checks.add("기존 세션으로 보호된 화면에 접근할 수 없다.");}
            case "LOGIN-06" -> {steps.add("로그인하지 않은 새 세션에서 문서·수집으로 확인한 보호 화면 URL을 직접 연다.");steps.add("응답 화면과 보호 정보의 노출 여부를 확인한다.");checks.add("인증 화면으로 이동하거나 접근 거부가 표시된다.");checks.add("보호된 정보와 조작 기능을 사용할 수 없다.");}
            case "LOGIN-08" -> {steps.add("허용·제한 역할별 시험 계정과 메뉴 접근표를 준비한다.");steps.add("계정별 독립 세션으로 로그인하고 허용 메뉴와 제한 URL에 각각 접근한다.");checks.add("각 역할의 허용 메뉴만 사용 가능하며 접근표와 일치한다.");checks.add("제한 URL 직접 접근도 거부되고 보호 정보가 노출되지 않는다.");}
            case "LOGIN-09" -> {steps.add("문서의 중복 로그인 허용·제한 정책을 확인하고 첫 세션에서 시험 계정으로 로그인한다.");steps.add("독립된 두 번째 브라우저 세션에서 같은 계정으로 로그인한다.");steps.add("두 세션에서 보호 화면을 다시 요청하고 인증 상태를 기록한다.");checks.add("두 세션의 허용·거부·만료 상태가 확인한 중복 로그인 정책과 일치한다. 기존 세션 종료를 임의로 기본 정책으로 가정하지 않는다.");}
            case "LOGIN-20" -> {steps.add("문서·설정의 세션 만료 시간과 갱신 조건을 확인하고 시험 계정으로 로그인한다.");steps.add("세션을 갱신하는 동작 없이 지정 시간 경과 후 보호 화면을 요청한다.");checks.add("지정된 만료 조건에서 재인증을 요구하고 기존 세션으로 보호 정보에 접근할 수 없다.");}
            case "LOGIN-23" -> {steps.add("비밀번호 변경 화면에서 새 비밀번호와 확인 필드에 서로 다른 시험값을 입력한다.");steps.add("변경을 한 번 제출하고 안내를 확인한다.");steps.add("독립 세션에서 기존 비밀번호로 인증하여 기존 값 유지 여부를 확인한다.");checks.add("비밀번호 불일치 안내가 표시되고 변경이 거부된다.");checks.add("기존 비밀번호가 유지되며 새 시험값으로 인증되지 않는다.");}
            default -> {steps.add("계정 상태·역할·정책 및 시험값을 준비한다: "+tc.path("templateOriginal").path("inputs").asText());steps.add("다음 시험을 수행하고 화면 또는 필요한 도구의 결과를 기록한다: "+tc.path("templateOriginal").path("scenario").asText());steps.add("정책에서 확인한 기대값과 실제 결과를 비교한다. 암호화 저장 등 화면 밖의 항목은 별도 증거를 확보한다.");checks.add(tc.path("expectedResult").asText());checks.add("정책·역할별 기대값과 실제 결과가 일치하며 비교 증거가 남는다. 화면 이동만으로 암호화·저장 정책을 충족했다고 판단하지 않는다.");}
        }
    }
    private static String number(List<String> values) {var out=new ArrayList<String>();for(int i=0;i<values.size();i++)out.add((i+1)+". "+values.get(i));return String.join("\n",out);}
}
