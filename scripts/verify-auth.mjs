// Run only against the disposable autotest-prodverify stack; see docs/AUTHENTICATION.md.
import assert from 'node:assert/strict'
import { createHash, X509Certificate } from 'node:crypto'
import { connect } from 'node:tls'
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import { setDefaultResultOrder } from 'node:dns'

setDefaultResultOrder('ipv4first')

const base = 'https://localhost:18443'
const username = process.env.AUTH_VERIFY_USERNAME
const password = process.env.AUTH_VERIFY_PASSWORD
assert.ok(username && password && process.env.NODE_EXTRA_CA_CERTS, 'Set verification credentials and NODE_EXTRA_CA_CERTS')
const requireCrawler = createRequire(new URL('../crawler-service/package.json', import.meta.url))
const { chromium } = requireCrawler('playwright')
const protocol = requireCrawler('./remote-agent/protocol.js')
async function api(path, { token, method = 'GET', body, headers = {} } = {}) {
  const response = await fetch(base + path, { method, headers: { ...(body ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers }, body: body ? JSON.stringify(body) : undefined })
  const text = await response.text()
  return { status: response.status, body: text ? JSON.parse(text) : null }
}
const denied = await api('/api/submissions/workspaces', { headers: { 'X-Member-Id': '1' } })
assert.equal(denied.status, 401)
assert.equal(denied.body.code, 'AUTH_REQUIRED')
const login = await api('/api/auth/login', { method: 'POST', body: { username, password, client: 'hub' } })
assert.equal(login.status, 200)
const adminToken = login.body.accessToken
assert.equal((await api('/api/auth/me', { token: adminToken })).body.memberId, 1)
const rotated = await api('/api/auth/refresh', { method: 'POST', body: { client: 'hub', refreshToken: login.body.refreshToken } })
assert.equal(rotated.status, 200)
assert.notEqual(rotated.body.refreshToken, login.body.refreshToken)
const agent = await api('/api/crawl-agents', { method: 'POST', token: adminToken, body: { name: 'Auth verification agent' } })
assert.equal(agent.status, 200)
const config = { serverUrl: base, agentId: agent.body.agent.agentId, token: agent.body.token }
assert.equal((await protocol.request(config, 'heartbeat')).accepted, true)
const wrong = { ...config, token: Buffer.alloc(32, 1).toString('base64url') }
await assert.rejects(protocol.request(wrong, 'heartbeat'))
const otherName = `verify-${Date.now()}`
const created = await api('/api/auth/users', { method: 'POST', token: adminToken, body: { username: otherName, password, displayName: 'Verification user', role: 'USER' } })
assert.equal(created.status, 201)
const other = await api('/api/auth/login', { method: 'POST', body: { username: otherName, password, client: 'hub' } })
assert.equal(other.status, 200)
const own = await api('/api/crawl-agents', { token: other.body.accessToken, headers: { 'X-Member-Id': '1', 'X-Internal-Token': 'forged' } })
assert.equal(own.status, 200)
assert.equal(own.body.agents.length, 0)
assert.equal((await api('/api/auth/users', { token: other.body.accessToken, method: 'POST', body: { username: 'forbidden', password, displayName: 'Denied', role: 'ADMIN' } })).status, 403)
assert.equal((await fetch(base + '/api/internal/playwright-crawls/events', { method: 'POST' })).status, 404)
console.log('HTTPS API: login, member 1, refresh, ownership, admin authorization, signed agent channel PASS')

// Validate TLS using the supplied CA, then pin that verified leaf key for this isolated browser.
const spki = await new Promise((resolve, reject) => {
  const socket = connect({ host: 'localhost', port: 18443, servername: 'localhost' }, () => {
    const cert = new X509Certificate(socket.getPeerCertificate().raw)
    resolve(createHash('sha256').update(cert.publicKey.export({ type: 'spki', format: 'der' })).digest('base64'))
    socket.end()
  })
  socket.on('error', reject)
})
const browser = await chromium.launch({ headless: true, args: [`--ignore-certificate-errors-spki-list=${spki}`, '--host-resolver-rules=MAP localhost 127.0.0.1'] })
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  const page = await context.newPage()
  const errors = []; page.on('pageerror', error => errors.push(error.message))
  await page.goto(base)
  await page.getByRole('heading', { name: 'autoTest 로그인' }).waitFor()
  await mkdir(new URL('../.runtime/', import.meta.url), { recursive: true })
  await page.screenshot({ path: new URL('../.runtime/auth-login.png', import.meta.url).pathname.replace(/^\/(\w:)/, '$1') })
  await page.getByLabel('사용자 이름').fill(username)
  await page.getByLabel('비밀번호').fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.getByRole('button', { name: '로그아웃', exact: true }).waitFor()
  assert.equal(await page.getByLabel('회원 ID', { exact: true }).count(), 0)
  const cookies = await context.cookies()
  const access = cookies.find(c => c.name === 'autotest_access')
  const refresh = cookies.find(c => c.name === 'autotest_refresh')
  assert.ok(access.httpOnly && access.secure && access.sameSite === 'Strict')
  assert.ok(refresh.httpOnly && refresh.secure && refresh.sameSite === 'Strict')
  assert.equal(await page.evaluate(() => document.cookie.includes('autotest_access')), false)
  // A generated, non-confidential PDF exercises actual UI upload and server-side extraction.
  const stream = 'BT /F1 12 Tf 40 100 Td (Authentication verification fixture.) Tj ET'
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>', `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`]
  let pdf = '%PDF-1.4\n'; const offsets = [0]
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${index + 1} 0 obj\n${object}\nendobj\n` })
  const xref = Buffer.byteLength(pdf)
  pdf += `xref\n0 6\n0000000000 65535 f \n${offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  await page.locator('input[type=file]').first().setInputFiles({ name: 'auth-fixture.pdf', mimeType: 'application/pdf', buffer: Buffer.from(pdf) })
  await page.locator('.upload-one').first().click()
  const uploaded = page.waitForResponse(response => response.url() === base + '/api/submissions' && response.request().method() === 'POST')
  await page.getByRole('button', { name: '선택한 문서 계속 업로드' }).click()
  const result = await uploaded
  assert.equal(result.status(), 200)
  const submission = await result.json()
  assert.equal(submission.documents[0].status, 'PARSED')
  assert.ok(submission.documents[0].storedPath.startsWith('s3://autotest-docs/'))
  await page.reload()
  await page.getByRole('button', { name: '로그아웃', exact: true }).waitFor()
  await page.getByText('auth-fixture.pdf', { exact: true }).first().waitFor()
  const foreign = await api(`/api/submissions/${submission.submissionId}`, { token: other.body.accessToken, headers: { 'X-Member-Id': '1' } })
  assert.ok([403, 404].includes(foreign.status), `Foreign submission response: ${foreign.status} ${foreign.body?.code}`)
  await context.clearCookies({ name: 'autotest_access' })
  const refreshed = page.waitForResponse(response => response.url().endsWith('/api/auth/refresh') && response.status() === 200)
  await page.reload(); await refreshed
  await page.getByRole('button', { name: '로그아웃', exact: true }).waitFor()
  assert.notEqual((await context.cookies()).find(c => c.name === 'autotest_refresh').value, refresh.value)
  await page.screenshot({ path: new URL('../.runtime/auth-workspace.png', import.meta.url).pathname.replace(/^\/(\w:)/, '$1') })
  await page.getByRole('button', { name: '로그아웃', exact: true }).click()
  await page.getByRole('heading', { name: 'autoTest 로그인' }).waitFor()
  assert.equal(errors.length, 0, errors.join('\n'))
  console.log('Browser: login, secure HttpOnly cookies, PDF upload/PARSED/S3, reload, automatic refresh, logout PASS')
} finally { await browser.close() }
