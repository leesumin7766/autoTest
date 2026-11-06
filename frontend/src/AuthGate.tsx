import { useEffect, useState, type FormEvent } from 'react'
import App from './App'
import { apiFetch, ApiError } from './apiFetch'

type User = { memberId: number; username: string; displayName: string; roles: string[] }

export function AuthGate() {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    const expired = () => { setUser(null); setError('로그인이 필요합니다.') }
    window.addEventListener('autotest:auth-required', expired)
    void apiFetch('/api/auth/me').then(response => response.json()).then(value => { if (active) setUser(value) })
      .catch(reason => { if (active && !(reason instanceof ApiError && reason.status === 401)) setError(reason.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false; window.removeEventListener('autotest:auth-required', expired) }
  }, [])
  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    const form = event.currentTarget
    const data = new FormData(form)
    try {
      const response = await apiFetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: data.get('username'), password: data.get('password'), client: 'web' }) })
      const value = await response.json(); form.reset(); setUser(value)
      try { localStorage.removeItem('autotest:last-member:v1') } catch { /* Storage is optional. */ }
    } catch (reason) { setError(reason instanceof Error ? reason.message : '로그인하지 못했습니다.') }
    finally { setBusy(false) }
  }
  async function logout() {
    setBusy(true); setError('')
    try { await apiFetch('/api/auth/logout', { method: 'POST' }); setUser(null) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '로그아웃하지 못했습니다.') }
    finally { setBusy(false) }
  }
  if (loading) return <main className="auth-card" aria-busy="true">로그인 상태를 확인하고 있습니다.</main>
  if (user) return <><div className="auth-session"><span>{user.displayName} ({user.username})</span><button type="button" disabled={busy} onClick={() => void logout()}>로그아웃</button>{error && <span role="alert">{error}</span>}</div><App key={user.memberId} memberId={String(user.memberId)} /></>
  return <main className="auth-card"><h1>autoTest 로그인</h1><p>관리자가 발급한 계정으로 로그인하세요.</p>
    <form onSubmit={event => void login(event)}>
      <label>사용자 이름<input name="username" autoComplete="username" required maxLength={64} disabled={busy} /></label>
      <label>비밀번호<input name="password" type="password" autoComplete="current-password" required disabled={busy} /></label>
      {error && <p role="alert">{error}</p>}
      <button type="submit" disabled={busy}>{busy ? '로그인 중…' : '로그인'}</button>
    </form></main>
}
