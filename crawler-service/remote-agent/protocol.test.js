const { test } = require('node:test');
const assert = require('node:assert/strict');
const { randomBytes } = require('node:crypto');
const { seal, open } = require('./protocol');
const { runJob } = require('./agent');

test('authenticated envelope rejects wrong key, modified payload and wrong request binding', () => {
  const token = randomBytes(32).toString('base64url');
  const envelope = seal(token, 'response\nnonce-a', { password: 'not-on-the-wire' });
  assert.equal(JSON.stringify(envelope).includes('not-on-the-wire'), false);
  assert.deepEqual(open(token, 'response\nnonce-a', envelope), { password: 'not-on-the-wire' });
  assert.throws(() => open(token, 'response\nnonce-b', envelope));
  assert.throws(() => open(randomBytes(32).toString('base64url'), 'response\nnonce-a', envelope));
  const corrupted = Buffer.from(envelope.ciphertext, 'base64'); corrupted[0] ^= 1;
  assert.throws(() => open(token, 'response\nnonce-a', { ...envelope, ciphertext: corrupted.toString('base64') }));
});
test('runner returns pages then completion and drops in-memory password', async () => {
  const events = [];
  const job = { jobId: 'job', claimToken: 'claim', password: 'secret' };
  await runJob({}, job, {
    request: async (_config, _action, event) => { events.push(event); return {}; },
    crawl: async (_job, { onPage }) => { await onPage({ pageIndex: 0 }); return { stopReason: 'PAGE_LIMIT' }; },
  });
  assert.deepEqual(events.map(event => event.event), ['page', 'complete']);
  assert.equal(events[1].stopReason, 'PAGE_LIMIT');
  assert.equal(job.password, undefined);
});
test('runner reports safe failure codes only and stops after lease rejection', async () => {
  const events = [];
  await runJob({}, { jobId: 'job', claimToken: 'claim' }, {
    request: async (_config, _action, event) => events.push(event),
    crawl: async () => { throw new Error('password=do-not-send'); },
  });
  assert.equal(events[0].errorCode, 'CRAWL_FAILED');
  assert.equal(JSON.stringify(events).includes('do-not-send'), false);
});
