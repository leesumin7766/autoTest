const { createCipheriv, createDecipheriv, randomBytes, randomUUID } = require('node:crypto');

function seal(token, aad, value) {
  const key = Buffer.from(token, 'base64url');
  if (key.length !== 32) throw new Error('AGENT_CONFIG_INVALID');
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  cipher.setAAD(Buffer.from(aad));
  const encrypted = Buffer.concat([cipher.update(JSON.stringify(value)), cipher.final(), cipher.getAuthTag()]);
  return { iv: iv.toString('base64'), ciphertext: encrypted.toString('base64') };
}
function open(token, aad, envelope) {
  const data = Buffer.from(envelope.ciphertext, 'base64');
  const decipher = createDecipheriv('aes-256-gcm', Buffer.from(token, 'base64url'), Buffer.from(envelope.iv, 'base64'));
  decipher.setAAD(Buffer.from(aad));
  decipher.setAuthTag(data.subarray(-16));
  return JSON.parse(Buffer.concat([decipher.update(data.subarray(0, -16)), decipher.final()]).toString());
}
async function request(config, action, body = {}) {
  const timestamp = Math.floor(Date.now() / 1000);
  const nonce = randomUUID();
  const aad = `autotest-agent-v1\n${config.agentId}\n${action}\n${timestamp}\n${nonce}`;
  const response = await fetch(`${config.serverUrl}/api/crawl-agents/${config.agentId}/channel/${action}`, {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(20000),
    headers: { 'Content-Type': 'application/json', 'X-Agent-Timestamp': String(timestamp), 'X-Agent-Nonce': nonce },
    body: JSON.stringify(seal(config.token, aad, body)),
  });
  if (!response.ok) {
    const error = new Error(response.status === 409 ? 'CRAWL_LEASE_LOST' : 'AGENT_REQUEST_FAILED');
    error.status = response.status;
    throw error;
  }
  return open(config.token, `response\n${nonce}`, await response.json());
}
module.exports = { seal, open, request };
