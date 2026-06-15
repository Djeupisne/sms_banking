import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const smsProcessingTime = new Trend('sms_processing_time');

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up to 10 users
    { duration: '1m', target: 50 },    // Ramp up to 50 users
    { duration: '2m', target: 100 },   // Ramp up to 100 users (stress test)
    { duration: '3m', target: 100 },   // Stay at 100 users
    { duration: '1m', target: 0 },     // Ramp down to 0 users
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests should complete below 500ms
    http_req_failed: ['rate<0.01'],    // Error rate must be less than 1%
    errors: ['rate<0.01'],             // Custom error rate threshold
    sms_processing_time: ['p(95)<300'], // SMS processing should be fast
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'test-api-key';

const headers = {
  'Content-Type': 'application/json',
  'X-API-Key': API_KEY,
};

// Test scenarios
export default function () {
  // Scenario 1: Health Check
  const healthRes = http.get(`${BASE_URL}/actuator/health`, { headers });
  check(healthRes, {
    'health check status is 200': (r) => r.status === 200,
  });
  errorRate.add(healthRes.status !== 200);

  sleep(0.5);

  // Scenario 2: Get Account Balance (Authenticated)
  const balanceRes = http.get(
    `${BASE_URL}/api/accounts/22899123456/balance`,
    { headers }
  );
  check(balanceRes, {
    'balance request status is 200 or 404': (r) => r.status === 200 || r.status === 404,
  });
  errorRate.add(balanceRes.status !== 200 && balanceRes.status !== 404);
  smsProcessingTime.add(balanceRes.timings.duration);

  sleep(0.5);

  // Scenario 3: Mini Statement
  const statementRes = http.get(
    `${BASE_URL}/api/accounts/22899123456/statements?limit=5`,
    { headers }
  );
  check(statementRes, {
    'statement request status is 200 or 404': (r) => r.status === 200 || r.status === 404,
  });
  errorRate.add(statementRes.status !== 200 && statementRes.status !== 404);

  sleep(0.5);

  // Scenario 4: Transfer Money (Simulation - will fail validation but tests endpoint)
  const transferPayload = JSON.stringify({
    fromAccount: '22899123456',
    toAccount: '22899654321',
    amount: 1000,
    reason: 'Test perf',
  });

  const transferRes = http.post(
    `${BASE_URL}/api/transfers`,
    transferPayload,
    { headers }
  );
  // We expect 400/401/403/404 in perf test without proper setup, just checking it responds
  check(transferRes, {
    'transfer endpoint responds': (r) => r.status >= 200 && r.status < 600,
  });
  errorRate.add(transferRes.status >= 500);

  sleep(1);
}

export function handleSummary(data) {
  return {
    'tests/performance/results/summary.json': JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}

function textSummary(data, options) {
  // Simple text summary for console output
  const { metrics } = data;
  let summary = '\n=== K6 Performance Test Summary ===\n';
  
  if (metrics.http_req_duration) {
    summary += `HTTP Request Duration (p95): ${metrics.http_req_duration.values['p(95)']}ms\n`;
  }
  if (metrics.http_req_failed) {
    summary += `Error Rate: ${(metrics.http_req_failed.values.rate * 100).toFixed(2)}%\n`;
  }
  if (metrics.sms_processing_time) {
    summary += `SMS Processing Time (p95): ${metrics.sms_processing_time.values['p(95)']}ms\n`;
  }
  
  summary += '=====================================\n';
  return summary;
}
