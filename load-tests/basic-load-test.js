import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Métriques custom
const errorRate = new Rate('errors');

// Configuration du test de charge basique
export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Montée progressive à 10 utilisateurs
    { duration: '1m', target: 10 },    // Maintien à 10 utilisateurs
    { duration: '30s', target: 0 },    // Descente progressive
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% des requêtes < 500ms
    http_req_failed: ['rate<0.01'],    // Moins de 1% d'erreurs
    errors: ['rate<0.01'],             // Moins de 1% d'erreurs custom
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Scénario de test : consultation de solde et historique
export default function () {
  const phoneNumber = __ENV.TEST_PHONE || '22507070707';
  const otp = __ENV.TEST_OTP || '123456';

  // Test health endpoint
  let res = http.get(`${BASE_URL}/actuator/health`);
  check(res, {
    'health check is OK': (r) => r.status === 200,
  });
  errorRate.add(res.status !== 200);
  sleep(0.5);

  // Test balance endpoint (simulé avec OTP fixe pour le test)
  res = http.get(`${BASE_URL}/api/sms/balance?phone=${phoneNumber}&otp=${otp}`);
  check(res, {
    'balance request successful': (r) => r.status === 200 || r.status === 401 || r.status === 403,
  });
  errorRate.add(res.status !== 200 && res.status !== 401 && res.status !== 403);
  sleep(1);

  // Test last transactions endpoint
  res = http.get(`${BASE_URL}/api/sms/transactions?phone=${phoneNumber}&otp=${otp}`);
  check(res, {
    'transactions request successful': (r) => r.status === 200 || r.status === 401 || r.status === 403,
  });
  errorRate.add(res.status !== 200 && res.status !== 401 && res.status !== 403);
  sleep(1);
}
