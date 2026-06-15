# Tests de Charge et Performance - Orabank SMS Banking

Ce dossier contient les tests de charge et de performance pour l'application Orabank SMS Banking.

## Prérequis

- k6 installé: `brew install k6` ou voir https://k6.io/docs/getting-started/installation/
- Application déployée et accessible

## Exécution des tests

### Test de charge basique
```bash
k6 run load-tests/basic-load-test.js
```

### Test de stress
```bash
k6 run load-tests/stress-test.js
```

### Test d'endurance (15 minutes)
```bash
k6 run load-tests/endurance-test.js
```

### Test de pic (Spike test)
```bash
k6 run load-tests/spike-test.js
```

## Interprétation des résultats

Les métriques clés à surveiller :
- **http_req_duration**: Latence des requêtes (p95 < 500ms)
- **http_req_failed**: Taux d'erreur (< 0.1%)
- **vus**: Nombre d'utilisateurs virtuels simultanés
- **iterations**: Nombre de transactions exécutées

## Seuils de performance

| Métrique | Seuil | Critique |
|----------|-------|----------|
| p95 latency | < 500ms | > 1000ms |
| Error rate | < 0.1% | > 1% |
| Throughput | > 100 req/s | < 50 req/s |
