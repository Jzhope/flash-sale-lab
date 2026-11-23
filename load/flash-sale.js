import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const base = __ENV.BASE_URL || 'http://localhost:8080';
const buyers = Number(__ENV.BUYERS || 120);
http.setResponseCallback(http.expectedStatuses(200, 201, 409));
export const options = {
  scenarios: { rush: { executor: 'per-vu-iterations', vus: buyers, iterations: 1, maxDuration: '60s' } },
  thresholds: { checks: ['rate>0.99'] },
};
export function setup() {
  const run = Date.now().toString(36);
  const tokens = [];
  for (let i = 0; i < buyers; i++) {
    const response = http.post(`${base}/api/auth/register`, JSON.stringify({
      username: `load_${run}_${i}`, password: 'load-demo-password',
    }), { headers: { 'Content-Type': 'application/json' } });
    if (response.status !== 201) throw new Error(`Cannot create buyer ${i}: ${response.status}`);
    tokens.push(response.json('accessToken'));
  }
  return { run, tokens };
}
export default function (data) {
  const buyer = exec.vu.idInTest - 1;
  const body = JSON.stringify({ productId: Number(__ENV.PRODUCT_ID || 1), quantity: 1 });
  const params = { headers: {
    'Content-Type': 'application/json', Authorization: `Bearer ${data.tokens[buyer]}`,
    'Idempotency-Key': `${data.run}-${buyer}`,
  } };
  const first = http.post(`${base}/api/orders`, body, params);
  check(first, { 'accepted or sold out': r => r.status === 201 || r.status === 409 });
  if (first.status === 201) {
    const retry = http.post(`${base}/api/orders`, body, params);
    check(retry, { 'same key returns same order': r => r.status === 201 && r.json('id') === first.json('id') });
  }
}
