// One client hammering the public seat finder must be throttled with 429 + Retry-After.
//
//   k6 run -e BASE_URL=http://localhost:8088 rate-limit.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';
const limited = new Counter('rate_limited');

export const options = {
  vus: 1,
  iterations: 100,
  thresholds: {
    // With the default 60 requests per minute, about 40 of 100 rapid requests are refused.
    rate_limited: ['count>=30'],
    checks: ['rate==1'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/public/status`, { headers: { 'X-Forwarded-For': '10.99.99.99' } });
  if (res.status === 429) {
    limited.add(1);
    check(res, { 'has Retry-After': (r) => r.headers['Retry-After'] !== undefined });
  } else {
    check(res, { 'status is 200': (r) => r.status === 200 });
  }
}
