# 🔄 Blue-Green Deployment - Orabank SMS Banking

## Vue d'Ensemble

Ce document décrit la procédure de déploiement blue-green pour Orabank SMS Banking, permettant des mises en production sans downtime et avec rollback instantané.

## Architecture Blue-Green

```
                    ┌─────────────────┐
                    │   Ingress NGINX │
                    │  (Load Balancer)│
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
    ┌─────────────────┐           ┌─────────────────┐
    │  Blue (Active)  │           │ Green (Standby) │
    │  v1.0.0         │           │ v1.1.0          │
    │  5 replicas     │           │ 5 replicas      │
    └─────────────────┘           └─────────────────┘
         ▲                             ▲
         │                             │
    ┌─────────────────────────────────────────┐
    │      Shared Resources                   │
    │  - PostgreSQL Database                  │
    │  - Redis Cluster                        │
    │  - External SMS/MobileMoney APIs        │
    └─────────────────────────────────────────┘
```

## Prérequis

- Kubernetes cluster avec au moins 2x la capacité normale
- Ingress NGINX configuré avec support du traffic splitting
- Monitoring actif (Prometheus + Grafana)
- Pipeline CI/CD fonctionnel

## Procédure de Déploiement

### Étape 1: Préparation

```bash
# Vérifier l'environnement actuel
kubectl get deployments -n orabank-sms-banking-prod
kubectl get pods -n orabank-sms-banking-prod -l app=orabank-sms-banking

# Version actuelle (Blue)
CURRENT_VERSION=$(kubectl get deployment prod-orabank-sms-banking -n orabank-sms-banking-prod -o jsonpath='{.spec.template.spec.containers[0].image}')
echo "Version actuelle: $CURRENT_VERSION"
```

### Étape 2: Déployer la nouvelle version (Green)

```bash
# Appliquer la configuration Green avec Kustomize
cd /workspace/k8s/overlays/production

# Créer le déploiement Green (sans traffic)
kubectl apply -k . --dry-run=client -o yaml | \
  sed 's/name: prod-orabank-sms-banking/name: green-orabank-sms-banking/g' | \
  kubectl apply -f -

# Ou utiliser Helm si configuré
helm upgrade --install green-orabank ./chart \
  --namespace orabank-sms-banking-prod \
  --set image.tag=v1.1.0 \
  --set replicaCount=5 \
  --set service.type=ClusterIP
```

### Étape 3: Validation de Green

```bash
# Attendre que Green soit ready
kubectl rollout status deployment/green-orabank-sms-banking -n orabank-sms-banking-prod

# Tests de santé sur Green
GREEN_POD=$(kubectl get pods -n orabank-sms-banking-prod -l app=orabank-sms-banking,version=green -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it $GREEN_POD -n orabank-sms-banking-prod -- curl -s localhost:8080/actuator/health

# Tests automatisés
./run-smoke-tests.sh --target=green --namespace=orabank-sms-banking-prod
```

### Étape 4: Basculer le traffic (Canary → 100%)

```bash
# Phase 1: 10% du traffic vers Green
kubectl annotate ingress prod-orabank-sms-banking \
  nginx.ingress.kubernetes.io/canary="true" \
  nginx.ingress.kubernetes.io/canary-weight="10" \
  -n orabank-sms-banking-prod --overwrite

# Monitorer pendant 5 minutes
# Vérifier erreurs, latence, métriques business

# Phase 2: 50% du traffic
kubectl annotate ingress prod-orabank-sms-banking \
  nginx.ingress.kubernetes.io/canary-weight="50" \
  -n orabank-sms-banking-prod --overwrite

# Monitorer pendant 10 minutes

# Phase 3: 100% du traffic
kubectl annotate ingress prod-orabank-sms-banking \
  nginx.ingress.kubernetes.io/canary-weight="100" \
  -n orabank-sms-banking-prod --overwrite
```

### Étape 5: Finalisation

```bash
# Mettre à jour le déploiement principal
kubectl set image deployment/prod-orabank-sms-banking \
  orabank-sms-banking=ghcr.io/orabank/sms-banking:v1.1.0 \
  -n orabank-sms-banking-prod

# Supprimer l'ancien déploiement Blue après 24h
kubectl delete deployment/blue-orabank-sms-banking -n orabank-sms-banking-prod

# Taguer l'ancienne version comme backup
docker tag ghcr.io/orabank/sms-banking:v1.0.0 ghcr.io/orabank/sms-banking:v1.0.0-backup
docker push ghcr.io/orabank/sms-banking:v1.0.0-backup
```

## Rollback d'Urgence

### Si problèmes détectés pendant Canary

```bash
# Retour immédiat à 100% Blue
kubectl annotate ingress prod-orabank-sms-banking \
  nginx.ingress.kubernetes.io/canary-weight="0" \
  -n orabank-sms-banking-prod --overwrite

# Ou supprimer complètement l'ingress canary
kubectl delete ingress green-orabank-sms-banking-canary -n orabank-sms-banking-prod

# Scale down Green
kubectl scale deployment/green-orabank-sms-banking --replicas=0 -n orabank-sms-banking-prod

# Notifier l'équipe
./notify-team.sh --severity=critical --message="Rollback triggered for v1.1.0"
```

### Rollback automatique (si configured)

```yaml
# Dans le deployment Green
spec:
  template:
    spec:
      containers:
      - name: orabank-sms-banking
        readinessProbe:
          failureThreshold: 3
          periodSeconds: 5
        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh","-c","sleep 30"]
```

## Monitoring Pendant le Déploiement

### Métriques à surveiller

1. **Disponibilité**
   - Taux d'erreurs HTTP < 0.1%
   - Health checks OK

2. **Performance**
   - p95 latency < 500ms
   - p99 latency < 1s

3. **Business**
   - Transactions réussies
   - OTP validation rate
   - Mobile Money success rate

### Dashboard de suivi

Accéder à Grafana: **Orabank Blue-Green Deployment Dashboard**

```
https://grafana.orabank.com/d/orabank-blue-green
```

## Checklist de Déploiement

- [ ] Backup base de données effectué
- [ ] Tests unitaires OK (>90% coverage)
- [ ] Tests d'intégration OK
- [ ] Tests de charge OK
- [ ] Security scan OK (OWASP)
- [ ] Documentation mise à jour
- [ ] Runbook mis à jour si nouveaux endpoints
- [ ] Équipe on-call informée
- [ ] Fenêtre de maintenance communiquée (si besoin)
- [ ] Plan de rollback testé

## Outils

### Script de déploiement automatisé

```bash
#!/bin/bash
# deploy-blue-green.sh

set -euo pipefail

NEW_VERSION=${1:-}
if [ -z "$NEW_VERSION" ]; then
    echo "Usage: $0 <new-version>"
    exit 1
fi

echo "🚀 Starting blue-green deployment to version $NEW_VERSION"

# Step 1: Deploy Green
echo "📦 Deploying Green environment..."
kubectl apply -k k8s/overlays/production --namespace orabank-sms-banking-prod

# Step 2: Wait for readiness
echo "⏳ Waiting for Green to be ready..."
kubectl rollout status deployment/green-orabank-sms-banking -n orabank-sms-banking-prod

# Step 3: Canary tests
echo "🧪 Running canary tests..."
./run-canary-tests.sh --version=$NEW_VERSION

# Step 4: Traffic switch (automated with monitoring)
echo "🔄 Switching traffic progressively..."
for weight in 10 50 100; do
    echo "  Setting traffic to ${weight}%..."
    kubectl annotate ingress prod-orabank-sms-banking \
      nginx.ingress.kubernetes.io/canary-weight="$weight" \
      -n orabank-sms-banking-prod --overwrite
    
    if [ $weight -lt 100 ]; then
        echo "  Monitoring for 5 minutes..."
        sleep 300
        
        # Check error rate
        ERROR_RATE=$(curl -s https://prometheus.orabank.com/api/v1/query \
          --data-urlencode "query=sum(rate(http_server_requests_seconds_count{status=~\"5..\",version=\"green\"}[5m]))" \
          | jq '.data.result[0].value[1]' | bc)
        
        if (( $(echo "$ERROR_RATE > 0.01" | bc -l) )); then
            echo "❌ Error rate too high! Rolling back..."
            ./rollback.sh
            exit 1
        fi
    fi
done

echo "✅ Deployment completed successfully!"
```

## Contact

Pour toute question sur cette procédure:
- Lead DevOps: devops-lead@orabank.com
- Channel Slack: #deployments

---

*Dernière mise à jour: 2024*
