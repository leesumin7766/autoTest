import { test } from 'node:test'
import assert from 'node:assert/strict'
import { apiFetch } from '../src/apiFetch.ts'

test('concurrent unauthorized requests rotate once and retry with web header', async () => {
  const original = globalThis.fetch
  let refreshed = false, rotations = 0
  globalThis.fetch = async (url, options = {}) => {
    if (url === '/api/auth/me') return new Response('{}', { status: refreshed ? 200 : 401 })
    if (url === '/api/auth/refresh') {
      rotations++; assert.equal(options.headers['X-AutoTest-Web'], '1')
      await new Promise(resolve => setTimeout(resolve, 10)); refreshed = true
      return new Response('{}')
    }
    assert.equal(options.headers.get('X-AutoTest-Web'), '1')
    assert.equal(options.credentials, 'same-origin')
    return new Response('{}', { status: refreshed ? 200 : 401 })
  }
  try {
    const results = await Promise.all([apiFetch('/api/submissions'), apiFetch('/api/crawl-agents')])
    assert.equal(rotations, 1); assert.ok(results.every(result => result.ok))
  } finally { globalThis.fetch = original }
})

test('failed refresh returns authentication error without looping', async () => {
  const original = globalThis.fetch
  let calls = 0
  globalThis.fetch = async () => { calls++; return new Response('{}', { status: 401 }) }
  try { await assert.rejects(apiFetch('/api/submissions'), error => error.status === 401); assert.equal(calls, 3) }
  finally { globalThis.fetch = original }
})

test('incorrect login does not attempt refresh', async () => {
  const original = globalThis.fetch
  let calls = 0
  globalThis.fetch = async () => { calls++; return new Response('{}', { status: 401 }) }
  try { await assert.rejects(apiFetch('/api/auth/login', { method: 'POST' })); assert.equal(calls, 1) }
  finally { globalThis.fetch = original }
})
