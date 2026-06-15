import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Métriques custom
const errorRate = new Rate('errors');

// Configuration du test d'endurance - 15 minutes de charge soutenue
export const options = {
  stages: [
    { duration: '2m', target: 30 },   // Montée progressive à 30 utilisateurs
    { duration: '13m', target: 30 },  // Maintien pendant 13 minutes (total 15 min)
    { duration: '2m', target: 0 },    // Descente progressive
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% des requêtes < 500ms
    http_req_failed: ['rate<0.01'],   // Moins de 1% d'erreurs
    errors: ['rate<0.01'],            // Moins de 1% d'erreurs custom
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Scénario de test : opérations courantes en continu
export default function () {
  const phoneNumber = __ENV.TEST_PHONE || '22507070707';
  const otp = __ENV.TEST_OTP || '123456';

  // Consultation solde (opération la plus fréquente)
  let res = http.get(`${BASE_URL}/api/sms/balance?phone=${phoneNumber}&otp=${otp}`);
  check(res, { 'balance OK': (r) => [200, 401, 403].includes(r.status) });
  errorRate.add(![200, 401, 403].includes(res.status));
  sleep(2);

  // Historique transactions
  res = http.get(`${BASE_URL}/api/sms/transactions?phone=${phoneNumber}&otp=${otp}`);
  check(res, { 'transactions OK': (r) => [200, 401, 403].includes(r.status) });
  errorRate.add(![200, 401, 403].includes(res.status));
  sleep(3);

  // Health check périodique
  res = http.get(`${BASE_URL}/actuator/health`);
  check(res, { 'health OK': (r) => r.status === 200 });
  errorRate.add(res.status !== 200);
  sleep(1);

  // Metrics endpoint
  res = http.get(`${BASE_URL}/actuator/prometheus`);
  check(res, { 'metrics OK': (r) => r.status === 200 });
  errorRate.add(res.status !== 200);
  sleep(2);
}
