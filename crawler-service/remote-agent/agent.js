const { readFile } = require('node:fs/promises');
const { request } = require('./protocol');
const { crawl } = require('./engine');
const { probe } = require('./probe');
const safeErrors = new Set(['LOGIN_FAILED', 'LOGIN_CONFIRMATION_REQUIRED', 'LOGIN_METHOD_UNSUPPORTED',
  'TARGET_UNREACHABLE', 'CERTIFICATE_ERROR', 'CAPTURE_TOO_LARGE', 'AGENT_DISCONNECTED', 'LOGIN_NOT_CONFIRMED', 'CONNECTION_SCOPE_BLOCKED', 'PAGE_LOAD_TIMEOUT']);

async function runJob(config, job, dependencies = {}) {
  const send = dependencies.request || request;
  const check = job.purpose === 'CONNECTION' || job.purpose === 'LOGIN';
  const execute = dependencies.crawl || (check ? probe : crawl);
  const controller = new AbortController();
  let inFlight = false;
  const event = body => send(config, 'event', { ...body, jobId: job.jobId, claimToken: job.claimToken });
  const heartbeat = setInterval(async () => {
    if (inFlight) return;
    inFlight = true;
    try { await event({ event: 'heartbeat' }); }
    catch { controller.abort(); }
    finally { inFlight = false; }
  }, 10000);
  try {
    const result = await execute(job, { signal: controller.signal, onPage: async page => {
      try { await event({ ...page, event: 'page' }); }
      catch (error) { if (error.status === 409) throw error; await event({ ...page, event: 'page' }); }
    } });
    await event({ event: 'complete', stopReason: result.stopReason, ...(check ? { result } : {}) });
  } catch (error) {
    if (error.message !== 'CRAWL_LEASE_LOST')
      await event({ event: 'failed', errorCode: safeErrors.has(error.message) ? error.message : 'CRAWL_FAILED' }).catch(() => {});
  } finally {
    clearInterval(heartbeat); controller.abort();
    delete job.password;
  }
}
async function main() {
  const config = JSON.parse(await readFile(process.argv[2] || './agent-config.json', 'utf8'));
  const server = new URL(config.serverUrl);
  if (!['http:', 'https:'].includes(server.protocol) || server.username || server.password || server.search || server.hash
      || !/^[0-9a-f-]{36}$/i.test(config.agentId) || Buffer.from(config.token || '', 'base64url').length !== 32)
    throw new Error('AGENT_CONFIG_INVALID');
  config.serverUrl = server.origin;
  console.log('AutoTest 실행기 시작 · 종료: Ctrl+C');
  let reported = false;
  while (true) {
    try {
      await request(config, 'heartbeat');
      if (!reported) { console.log('관리 서버 연결 완료 · 작업 대기'); reported = true; }
      const job = await request(config, 'claim');
      if (job.jobId) { console.log('화면 수집 시작'); await runJob(config, job); console.log('작업 종료 · 다음 작업 대기'); }
    } catch { if (reported) console.log('서버 연결 확인 중 · 자동 재시도'); reported = false; }
    await new Promise(resolve => setTimeout(resolve, 5000));
  }
}
if (require.main === module) main().catch(() => { console.error('실행기 설정 파일·Node.js·Chromium 설치를 확인해 주세요.'); process.exitCode = 1; });
module.exports = { runJob };
