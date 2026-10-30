export type ReviewManual = { fileId: string; originalFilename: string; status: string; format: string }

export function ManualReviewPanel({ manual, onDocuments }: { manual?: ReviewManual; onDocuments: () => void }) {
  if (!manual || manual.status !== 'PARSED') return <div className="workspace-empty">
    <h2>제품 매뉴얼을 등록해 주세요</h2>
    <p>01 자료 등록에서 제품 매뉴얼의 업로드와 문서 처리를 완료하면 검토 대상을 확인할 수 있습니다.</p>
    <button type="button" className="primary-button" onClick={onDocuments}>자료 등록으로 이동</button>
  </div>
  return <section className="manual-review-panel" aria-label="매뉴얼 검토 설정">
    <div className="manual-review-target">
      <div><h2>검토 대상 매뉴얼</h2><p>{manual.originalFilename}</p><small>{manual.format.toUpperCase()} · 등록 완료</small></div>
      <span className="workspace-badge">자동 검토 미연동</span>
    </div>
    <p>화면 수집이나 05 시험 실행 없이, 등록된 제품 매뉴얼을 대상으로 검토합니다.</p>
    <div className="manual-review-scopes">
      <article><h3>오탈자 검토</h3><p>철자 오류와 중복·누락된 글자 등 오탈자 후보를 확인합니다.</p><small>제품명·전문 용어는 문맥을 확인한 뒤 검토합니다.</small></article>
      <article><h3>지원 종료 SW 표기 검토</h3><p>지원 환경에 기재된 운영체제·소프트웨어의 지원 종료 여부를 확인합니다.</p><small>제품·버전·에디션·지원 유형과 기준일을 확인해 후보로 제시합니다.</small></article>
    </div>
    <div className="manual-review-pending" role="status">
      <h3>검토 결과 대기</h3>
      <p>아직 검사를 수행하지 않았습니다. 자동 검토 기능을 연결한 뒤 오탈자와 지원 종료 SW 표기 후보가 표시됩니다.</p>
      <button type="button" className="primary-button" disabled>자동 검토 준비 중</button>
    </div>
  </section>
}
