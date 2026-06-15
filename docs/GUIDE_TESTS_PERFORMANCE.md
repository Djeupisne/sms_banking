# Guide de Test de Performance - Orabank SMS Banking

## Prérequis

1. **Installer k6**
   ```bash
   # macOS
   brew install k6
   
   # Linux (Debian/Ubuntu)
   sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5A1AB34F2D7EB9C
   echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
   sudo apt-get update
   sudo apt-get install k6
   
   # Docker
   docker run --rm grafana/k6 version
   ```

2. **Démarrer l'application**
   ```bash
   docker-compose up -d
   ```

3. **Attendre que l'application soit prête**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

## Exécution des Tests

### Test de Charge Standard
```bash
export BASE_URL=http://localhost:8080
export API_KEY=votre-api-key-test

k6 run tests/performance/sms-banking-load-test.js
```

### Test avec Docker
```bash
docker run --rm \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e API_KEY=test-api-key \
  -v $(pwd)/tests/performance:/scripts \
  grafana/k6 run /scripts/sms-banking-load-test.js
```

### Test de Stress (Charge Maximale)
```bash
k6 run --vus 200 --duration 5m tests/performance/sms-banking-load-test.js
```

### Test d'Endurance (Longue Durée)
```bash
k6 run --vus 50 --duration 30m tests/performance/sms-banking-load-test.js
```

### Test Spike (Pic de Charge Soudain)
```bash
k6 run --vus 0 --max-vus 100 --stage-duration-ramp-up 10s tests/performance/sms-banking-load-test.js
```

## Analyse des Résultats

Les résultats sont générés dans `tests/performance/results/summary.json`

### Métriques Clés à Surveiller

1. **HTTP Request Duration (p95)**: Doit être < 500ms
2. **Error Rate**: Doit être < 1%
3. **SMS Processing Time (p95)**: Doit être < 300ms
4. **Requests/sec**: Capacité de traitement

### Seuils de Performance

| Métrique | Excellent | Acceptable | Critique |
|----------|-----------|------------|----------|
| Latence p95 | < 200ms | 200-500ms | > 500ms |
| Taux d'erreur | < 0.1% | 0.1-1% | > 1% |
| Throughput | > 100 req/s | 50-100 req/s | < 50 req/s |

## Intégration CI/CD

Le test est automatiquement exécuté dans le pipeline GitHub Actions lors des merges sur `main`.

```yaml
- name: Run Performance Tests
  run: |
    docker run --rm \
      -e BASE_URL=http://app:8080 \
      -e API_KEY=test-key \
      -v $(pwd)/tests/performance:/scripts \
      grafana/k6 run /scripts/sms-banking-load-test.js
```

## Scénarios de Test Détaillés

### 1. Consultation de Solde
- **Endpoint**: `GET /api/accounts/{phoneNumber}/balance`
- **Charge attendue**: 60% du trafic total
- **Performance cible**: < 200ms p95

### 2. Mini-Relevé
- **Endpoint**: `GET /api/accounts/{phoneNumber}/statements`
- **Charge attendue**: 25% du trafic total
- **Performance cible**: < 300ms p95

### 3. Virement
- **Endpoint**: `POST /api/transfers`
- **Charge attendue**: 10% du trafic total
- **Performance cible**: < 500ms p95 (inclut validation + transaction)

### 4. Health Checks
- **Endpoint**: `GET /actuator/health`
- **Charge attendue**: 5% du trafic total
- **Performance cible**: < 50ms p95

## Optimisations Recommandées

Si les seuils ne sont pas atteints:

1. **Vérifier la configuration Redis**
   - Augmenter `maxmemory`
   - Activer la persistance AOF

2. **Optimiser PostgreSQL**
   - Ajouter des index sur `phone_number`, `created_at`
   - Ajuster `shared_buffers` et `work_mem`

3. **Ajuster les paramètres JVM**
   ```bash
   -Xms512m -Xmx2g -XX:+UseG1GC
   ```

4. **Scale horizontal**
   - Augmenter le nombre de réplicas dans Kubernetes
   - Configurer HPA (Horizontal Pod Autoscaler)

## Reporting

Générer un rapport HTML:
```bash
k6 run --out json=results.json tests/performance/sms-banking-load-test.js
k6-to-html results.json > performance-report.html
```

## Contact

Pour toute question sur les tests de performance, contacter l'équipe DevOps.
