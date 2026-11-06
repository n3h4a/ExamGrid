// Result day: 1,000 students look up their seats at the same time.
//
//   k6 run -e BASE_URL=http://localhost:8088 -e ROLLS=rolls.txt seat-search.js
//
// rolls.txt holds one roll number per line (see README). Each virtual user is a student on
// their own network address, simulated with X-Forwarded-For, which the edge Nginx trusts only
// from the private network, so the per-IP rate limit does not throttle the test.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';
// MODE=stress: 200 users with no think time, to find the throughput ceiling.
const STRESS = __ENV.MODE === 'stress';
const rolls = new SharedArray('rolls', () =>
  open(__ENV.ROLLS || 'rolls.txt').split('\n').map((r) => r.trim()).filter(Boolean),
);

export const options = {
  scenarios: STRESS ? {
    stress: { executor: 'constant-vus', vus: 200, duration: '30s' },
  } : {
    result_day: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 1000 },
        { duration: '40s', target: 1000 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<250', 'p(99)<500'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const roll = rolls[Math.floor(Math.random() * rolls.length)];
  const ip = `10.${__VU % 250}.${Math.floor(__VU / 250)}.${(__ITER % 250) + 1}`;
  const res = http.get(`${BASE_URL}/api/public/seat?roll=${roll}`, {
    headers: { 'X-Forwarded-For': ip },
    tags: { name: 'GET /api/public/seat' },
  });
  check(res, {
    'status is 200': (r) => r.status === 200,
    'has exams': (r) => r.status === 200 && r.json('exams').length > 0,
    'no names leaked': (r) => !r.body.includes('"name"'),
  });
  // A student reads the answer before trying again.
  if (!STRESS) sleep(0.5 + Math.random());
}
