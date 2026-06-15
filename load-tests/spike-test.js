import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Métriques custom
const errorRate = new Rate('errors');

// Configuration du test de pic (Spike test) - montée très rapide de charge
export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Charge normale
    { duration: '1m', target: 10 },    // Maintien charge normale
    { duration: '30s', target: 150 },  // Pic soudain (spike) à 150 utilisateurs
    { duration: '2m', target: 150 },   // Maintien du pic
    { duration: '30s', target: 10 },   // Retour à la normale
    { duration: '1m', target: 10 },    // Vérification récupération
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500'], // Plus tolérant pendant le spike
    http_req_failed: ['rate<0.1'],     // Tolère jusqu'à 10% d'erreurs pendant le spike
    errors: ['rate<0.1'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Scénario de test : opérations variées avec pic soudain
export default function () {
  const phoneNumber = __ENV.TEST_PHONE || '22507070707';
  const recipientPhone = __ENV.TEST_RECIPIENT_PHONE || '22508080808';
  const otp = __ENV.TEST_OTP || '123456';

  // Health check
  let res = http.get(`${BASE_URL}/actuator/health`);
  check(res, { 'health OK': (r) => r.status === 200 });
  errorRate.add(res.status !== 200);

  // Consultation solde
  res = http.get(`${BASE_URL}/api/sms/balance?phone=${phoneNumber}&otp=${otp}`);
  check(res, { 'balance OK': (r) => [200, 401, 403].includes(r.status) });
  errorRate.add(![200, 401, 403].includes(res.status));
  sleep(0.5);

  // Virement (opération critique - importante pendant les pics)
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
    'transfer OK': (r) => [200, 202, 400, 401, 403, 422, 503].includes(r.status) 
  });
  errorRate.add(![200, 202, 400, 401, 403, 422, 503].includes(res.status));
  sleep(1);

  // Circuit breaker devrait se déclencher si nécessaire
  res = http.get(`${BASE_URL}/actuator/circuitbreakers`);
  if (res.status === 200) {
    console.log('Circuit breakers status available');
  }
  sleep(0.5);
}
