import assert from 'node:assert/strict'
import fs from 'node:fs'
import { apiFetch, ApiError, responseError } from '../frontend/src/apiFetch.ts'
import { serviceErrorMessages } from '../frontend/src/serviceErrorMessages.ts'

const java = fs.readFileSync(new URL('../test-management-service/src/main/java/com/autotest/test_management_service/exception/TestManagementErrorCode.java', import.meta.url), 'utf8')
const expected = Object.fromEntries([...java.matchAll(/    ([A-Z0-9_]+)\("((?:[^"\\]|\\.)*)", HttpStatus\./g)].map(m => [m[1], JSON.parse(`"${m[2]}"`)]))
assert.deepEqual(serviceErrorMessages, expected, 'Frontend public messages must match the service catalog')
for (const [code, message] of Object.entries(expected)) assert.equal(responseError(400, { code, message: 'password=secret jdbc:internal' }).message, message)
assert.equal(responseError(503, '<html>secret private-host</html>').message, '현재 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.')
assert.ok(!responseError(500, { message: 'secret stack SQL', traceId: 'password' }).message.includes('secret'))
assert.equal(responseError(500, { traceId: 'password' }).traceId, undefined)
const original = globalThis.fetch
try {
  globalThis.fetch = async () => { throw new TypeError('Failed to fetch private-host') }
  await assert.rejects(apiFetch('/api/submissions'), e => e instanceof ApiError && e.code === 'NETWORK_UNAVAILABLE' && !e.message.includes('private-host'))
  globalThis.fetch = async () => { throw new DOMException('cancelled', 'AbortError') }
  await assert.rejects(apiFetch('/api/submissions'), e => e.name === 'AbortError')
  globalThis.fetch = async () => { throw new DOMException('private timeout', 'TimeoutError') }
  await assert.rejects(apiFetch('/api/submissions'), e => e.code === 'NETWORK_TIMEOUT')
  globalThis.fetch = async () => new Response('<html>secret stack</html>', { status: 502 })
  await assert.rejects(apiFetch('/api/submissions'), e => e.status === 502 && !e.message.includes('secret'))
  globalThis.fetch = async () => Response.json({ code: 'DOCUMENT_FORMAT_UNSUPPORTED', message: 'unsafe backend details' }, { status: 400 })
  await assert.rejects(apiFetch('/api/submissions'), e => e.message === expected.DOCUMENT_FORMAT_UNSUPPORTED)
  globalThis.fetch = async () => new Response('download bytes', { status: 200 })
  assert.equal(await (await apiFetch('/api/download')).text(), 'download bytes')
} finally { globalThis.fetch = original }
console.log(`PASS: ${Object.keys(expected).length} service message mappings; network/timeout/cancel, proxy HTML, unknown errors, upload errors and download preservation`)
