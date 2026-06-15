import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Métriques custom
const errorRate = new Rate('errors');

// Configuration du test de stress - pousse le système à ses limites
export const options = {
  stages: [
    { duration: '1m', target: 50 },    // Montée rapide à 50 utilisateurs
    { duration: '2m', target: 100 },   // Montée à 100 utilisateurs
    { duration: '3m', target: 100 },   // Maintien à 100 utilisateurs (stress)
    { duration: '1m', target: 200 },   // Pic extrême à 200 utilisateurs
    { duration: '1m', target: 0 },     // Descente progressive
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 95% des requêtes < 1s (plus tolérant en stress)
    http_req_failed: ['rate<0.05'],    // Moins de 5% d'erreurs
    errors: ['rate<0.05'],             // Moins de 5% d'erreurs custom
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Scénario de test : opérations complètes (solde, transactions, virement)
export default function () {
  const phoneNumber = __ENV.TEST_PHONE || '22507070707';
  const recipientPhone = __ENV.TEST_RECIPIENT_PHONE || '22508080808';
  const otp = __ENV.TEST_OTP || '123456';

  // Test health endpoint
  let res = http.get(`${BASE_URL}/actuator/health`);
  check(res, { 'health OK': (r) => r.status === 200 });
  errorRate.add(res.status !== 200);
  sleep(0.2);

  // Consultation solde
  res = http.get(`${BASE_URL}/api/sms/balance?phone=${phoneNumber}&otp=${otp}`);
  check(res, { 'balance OK': (r) => [200, 401, 403].includes(r.status) });
  errorRate.add(![200, 401, 403].includes(res.status));
  sleep(0.5);

  // Historique transactions
  res = http.get(`${BASE_URL}/api/sms/transactions?phone=${phoneNumber}&otp=${otp}`);
  check(res, { 'transactions OK': (r) => [200, 401, 403].includes(r.status) });
  errorRate.add(![200, 401, 403].includes(res.status));
  sleep(0.5);

  // Simulation envoi SMS (webhook)
  const smsPayload = {
    sender: phoneNumber,
    message: 'SOLDE',
    timestamp: new Date().toISOString(),
  };
  
  res = http.post(`${BASE_URL}/api/webhooks/sms`, JSON.stringify(smsPayload), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 
    'webhook SMS OK': (r) => [200, 202, 400, 401].includes(r.status) 
  });
  errorRate.add(![200, 202, 400, 401].includes(res.status));
  sleep(1);

  // Virement (opération critique)
  const transferPayload = {
    senderPhone: phoneNumber,
    recipientPhone: recipientPhone,
    amount: 1000,
    otp: otp,
  };
  
  res = http.post(`${BASE_URL}/api/sms/transfer`, JSON.stringify(transferPayload), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 
    'transfer OK': (r) => [200, 202, 400, 401, 403, 422].includes(r.status) 
  });
  errorRate.add(![200, 202, 400, 401, 403, 422].includes(res.status));
  sleep(1.5);
}
