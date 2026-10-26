import { useEffect, useState } from 'react'
import { apiFetch as fetch } from './apiFetch'
export type EvidenceGroup = { featureId: string; caseId?: string; evidenceUrl?: string; evidence: {id:string;kind:string;url?:string;text?:string;screenshot?:string;records?:unknown[]}[]; plans: unknown[] }

export function useExecutionEvidence(group: EvidenceGroup | undefined, memberId: string) {
  const [payload,setPayload]=useState<{evidence:EvidenceGroup['evidence'];plans:unknown[]}|null>(null)
  const [error,setError]=useState(''),[loading,setLoading]=useState(false)
  useEffect(()=>{
    setPayload(null);setError('');setLoading(false)
    if(!group?.evidenceUrl)return
    const controller=new AbortController();setLoading(true)
    void fetch(group.evidenceUrl,{headers:{'X-Member-Id':memberId},signal:controller.signal})
      .then(async response=>{if(!response.ok)throw Error('수행 증거를 불러오지 못했습니다.');return response.json()})
      .then(value=>{if(!controller.signal.aborted)setPayload(value)})
      .catch(error=>{if(!controller.signal.aborted)setError(error.message)})
      .finally(()=>{if(!controller.signal.aborted)setLoading(false)})
    return ()=>controller.abort()
  },[group?.evidenceUrl,memberId])
  return {group:group?{...group,...(payload||{})}:undefined,error,loading}
}
