# 🚨 Runbook d'Incident - Orabank SMS Banking

## Table des Matières

1. [Application DOWN](#application-down)
2. [Latence Élevée](#latence-élevée)
3. [Taux d'Erreur HTTP 5xx](#taux-derreur-http-5xx)
4. [Circuit Breaker Ouvert](#circuit-breaker-ouvert)
5. [Base de Données - Connexions Épuisées](#base-de-données---connexions-épuisées)
6. [Redis DOWN](#redis-down)
7. [Échecs OTP Élevés](#échecs-otp-élevés)
8. [Transactions Mobile Money en Échec](#transactions-mobile-money-en-échec)

---

## Application DOWN

### 🔴 Sévérité: CRITIQUE

**Alerte:** `OrabankApplicationDown`

### Symptômes
- L'application ne répond plus aux health checks
- Toutes les requêtes HTTP échouent
- Les utilisateurs ne peuvent pas accéder au service SMS banking

### Diagnostic

```bash
# Vérifier le statut des pods
kubectl get pods -n orabank-sms-banking-prod

# Voir les logs du dernier redémarrage
kubectl logs -n orabank-sms-banking-prod -l app=orabank-sms-banking --tail=200

# Vérifier les événements Kubernetes
kubectl get events -n orabank-sms-banking-prod --sort-by='.lastTimestamp'

# Tester l'endpoint health
curl -v https://api.orabank.com/actuator/health
```

### Actions Correctives

1. **Redémarrage rapide (si pod crashé):**
```bash
kubectl rollout restart deployment/prod-orabank-sms-banking -n orabank-sms-banking-prod
```

2. **Vérifier la base de données:**
```bash
kubectl get pods -n postgresql
kubectl logs -n postgresql -l app=postgres --tail=100
```

3. **Vérifier Redis:**
```bash
kubectl get pods -n redis
kubectl exec -it -n redis redis-master-0 -- redis-cli ping
```

4. **Scaler temporairement si besoin:**
```bash
kubectl scale deployment/prod-orabank-sms-banking --replicas=2 -n orabank-sms-banking-prod
```

### Escalade
- Si non résolu en 5 minutes → Équiper on-call + Lead Backend
- Si non résolu en 15 minutes → CTO + Tous les stakeholders

---

## Latence Élevée

### ⚠️ Sévérité: WARNING / CRITIQUE

**Alertes:** `OrabankHighLatencyP95`, `OrabankCriticalLatencyP95`

### Symptômes
- p95 latency > 1s (warning) ou > 2s (critique)
- Ralentissement général des opérations
- Timeouts occasionnels

### Diagnostic

```bash
# Vérifier les métriques de latence dans Prometheus
# Accéder à Grafana: Dashboard "Orabank Overview"

# Vérifier l'utilisation CPU/Mémoire
kubectl top pods -n orabank-sms-banking-prod

# Voir les slow queries database
kubectl logs -n orabank-sms-banking-prod -l app=orabank-sms-banking | grep "slow"

# Vérifier les connexions DB
curl -s https://api.orabank.com/actuator/metrics/hikaricp.connections.active
```

### Actions Correctives

1. **Identifier le goulot d'étranglement:**
   - Si CPU > 80% → Augmenter les ressources
   - Si mémoire > 85% → Augmenter heap JVM
   - Si DB connections > 90% → Optimiser requêtes

2. **Augmenter les ressources (si nécessaire):**
```bash
kubectl set resources deployment/prod-orabank-sms-banking \
  -c orabank-sms-banking \
  --requests=cpu=2000m,memory=3Gi \
  --limits=cpu=6000m,memory=6Gi \
  -n orabank-sms-banking-prod
```

3. **Activer le circuit breaker manuellement (si cascade failure):**
```bash
# Via JMX ou endpoint admin (si disponible)
curl -X POST https://api.orabank.com/admin/circuit-breaker/force-open
```

4. **Réduire le traffic (en dernier recours):**
```bash
# Rate limiting via ingress
kubectl annotate ingress prod-orabank-sms-banking \
  nginx.ingress.kubernetes.io/rate-limit="50" \
  -n orabank-sms-banking-prod --overwrite
```

### Escalade
- Warning > 30 min → Lead Backend
- Critique > 10 min → Équipe complète

---

## Taux d'Erreur HTTP 5xx

### 🔴 Sévérité: CRITIQUE

**Alerte:** `OrabankHighErrorRate`

### Symptômes
- Taux d'erreurs 5xx > 5%
- Logs d'erreurs dans l'application
- Utilisateurs signalant des échecs

### Diagnostic

```bash
# Voir les erreurs récentes
kubectl logs -n orabank-sms-banking-prod -l app=orabank-sms-banking | grep "ERROR" | tail -50

# Compter les erreurs par type
kubectl logs -n orabank-sms-banking-prod -l app=orabank-sms-banking | grep "500\|502\|503\|504" | wc -l

# Vérifier les dépendances externes
curl -v https://sms-provider.api.com/health
curl -v https://mobilemoney.provider.com/api/status
```

### Actions Correctives

1. **Identifier la source des erreurs:**
   - Erreurs DB → Vérifier PostgreSQL
   - Erreurs Redis → Vérifier Redis
   - Erreurs externes → Activer circuit breakers

2. **Activer les fallbacks:**
```bash
# Si Redis est down, basculer en in-memory
kubectl set env deployment/prod-orabank-sms-banking \
  REDIS_FALLBACK_ENABLED=true \
  -n orabank-sms-banking-prod
```

3. **Isoler le service défaillant:**
```bash
# Ouvrir manuellement un circuit breaker
curl -X POST https://api.orabank.com/actuator/circuitbreakers/mobileMoneyService/state \
  -H "Content-Type: application/json" \
  -d '{"state": "FORCED_OPEN"}'
```

### Escalade
- Si erreurs DB → DBA + Lead Backend
- Si erreurs externes → Fournisseur + Lead Integration

---

## Circuit Breaker Ouvert

### 🔴 Sévérité: CRITIQUE

**Alerte:** `OrabankCircuitBreakerOpen`

### Symptômes
- Un ou plusieurs circuit breakers sont ouverts
- Certaines fonctionnalités sont désactivées
- Logs indiquant "Circuit breaker is open"

### Diagnostic

```bash
# Voir l'état des circuit breakers
curl -s https://api.orabank.com/actuator/circuitbreakers | jq .

# Identifier quel service est affecté
kubectl logs -n orabank-sms-banking-prod -l app=orabank-sms-banking | grep "Circuit breaker.*OPEN"
```

### Actions Correctives

1. **Identifier la cause racine:**
   - MobileMoneyService → Problème fournisseur payment
   - SmsGatewayService → Problème provider SMS
   - DatabaseService → Problème PostgreSQL

2. **Résoudre le problème sous-jacent:**
   - Contacter le fournisseur externe
   - Redémarrer le service dépendant
   - Vérifier la connectivité réseau

3. **Reset manuel (après résolution):**
```bash
# Reset d'un circuit breaker spécifique
curl -X POST https://api.orabank.com/actuator/circuitbreakers/mobileMoneyService/state \
  -H "Content-Type: application/json" \
  -d '{"state": "CLOSED"}'
```

### Escalade
- Si fournisseur externe → Contact support fournisseur
- Si interne → Lead Backend + Infrastructure

---

## Base de Données - Connexions Épuisées

### 🔴 Sévérité: CRITIQUE

**Alerte:** `OrabankDatabaseConnectionsExhausted`

### Symptômes
- HikariCP connections > 90%
- Timeouts sur les requêtes SQL
- Ralentissement général

### Diagnostic

```bash
# Vérifier le pool de connexions
curl -s https://api.orabank.com/actuator/metrics/hikaricp.connections.active
curl -s https://api.orabank.com/actuator/metrics/hikaricp.connections.max

# Voir les requêtes en cours sur PostgreSQL
kubectl exec -it -n postgresql postgres-primary-0 -- psql -U postgres -c "SELECT * FROM pg_stat_activity;"

# Identifier les locks
kubectl exec -it -n postgresql postgres-primary-0 -- psql -U postgres -c "SELECT * FROM pg_locks WHERE granted = false;"
```

### Actions Correctives

1. **Tuer les requêtes bloquantes:**
```sql
-- Dans PostgreSQL
SELECT pg_terminate_backend(pid) 
FROM pg_stat_activity 
WHERE state = 'idle in transaction' 
AND query_start < NOW() - INTERVAL '5 minutes';
```

2. **Augmenter le pool de connexions (temporaire):**
```bash
kubectl set env deployment/prod-orabank-sms-banking \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=50 \
  -n orabank-sms-banking-prod
kubectl rollout restart deployment/prod-orabank-sms-banking -n orabank-sms-banking-prod
```

3. **Scaler horizontalement:**
```bash
kubectl scale deployment/prod-orabank-sms-banking --replicas=8 -n orabank-sms-banking-prod
```

### Escalade
- Immédiate → DBA + Lead Backend
- Si persistant > 30 min → Architecture review

---

## Redis DOWN

### 🔴 Sévérité: CRITIQUE

**Alerte:** `OrabankRedisDown`

### Symptômes
- Redis ne répond pas
- Rate limiting fallback activé
- Sessions perdues

### Diagnostic

```bash
# Vérifier le pod Redis
kubectl get pods -n redis
kubectl describe pod -n redis redis-master-0

# Tester la connectivité
kubectl exec -it -n redis redis-master-0 -- redis-cli ping

# Voir les logs Redis
kubectl logs -n redis redis-master-0
```

### Actions Correctives

1. **Redémarrer Redis:**
```bash
kubectl delete pod -n redis redis-master-0
```

2. **Basculer en mode dégradé (in-memory):**
```bash
kubectl set env deployment/prod-orabank-sms-banking \
  REDIS_ENABLED=false \
  RATE_LIMITER_TYPE=in_memory \
  -n orabank-sms-banking-prod
kubectl rollout restart deployment/prod-orabank-sms-banking -n orabank-sms-banking-prod
```

### Escalade
- Immédiate → Infrastructure Team
- Si données critiques → DBA Redis

---

## Échecs OTP Élevés

### ⚠️ Sévérité: WARNING

**Alerte:** `OrabankHighOTPFailures`

### Symptômes
- Taux d'échec OTP > 30%
- Utilisateurs ne pouvant pas s'authentifier
- Possible attaque par force brute

### Diagnostic

```bash
# Vérifier les logs d'échec OTP
kubectl logs -n orabank-sms-banking-prod -l app=orabank-sms-banking | grep "OTP.*FAILED" | tail -50

# Identifier les numéros suspects
kubectl logs -n orabank-sms-banking-prod -l app=orabank-sms-banking | grep "OTP.*FAILED" | awk '{print $NF}' | sort | uniq -c | sort -rn | head -20

# Vérifier si attaque DDoS
kubectl top pods -n orabank-sms-banking-prod
```

### Actions Correctives

1. **Renforcer le rate limiting:**
```bash
kubectl annotate ingress prod-orabank-sms-banking \
  nginx.ingress.kubernetes.io/rate-limit="20" \
  nginx.ingress.kubernetes.io/rate-limit-window="1m" \
  -n orabank-sms-banking-prod --overwrite
```

2. **Bannir les IPs suspectes:**
```bash
# Ajouter au denylist (via configmap ou WAF)
kubectl edit configmap/nginx-configuration -n ingress-nginx
```

3. **Notifier la sécurité:**
- Envoyer alerte à l'équipe Security
- Préparer rapport d'incident

### Escalade
- Si attaque confirmée → CISO + Security Team
- Si problème technique → Lead Backend

---

## Transactions Mobile Money en Échec

### 🔴 Sévérité: CRITIQUE

**Alerte:** `OrabankMobileMoneyFailures`

### Symptômes
- Taux d'échec > 10%
- Utilisateurs ne pouvant pas faire de transactions
- Impact financier direct

### Diagnostic

```bash
# Vérifier les logs Mobile Money
kubectl logs -n orabank-sms-banking-prod -l app=orabank-sms-banking | grep "MobileMoney.*FAILED" | tail -50

# Tester la connectivité au provider
curl -v https://mobilemoney.provider.com/api/health

# Vérifier le circuit breaker
curl -s https://api.orabank.com/actuator/circuitbreakers/mobileMoneyService
```

### Actions Correctives

1. **Contacter le fournisseur Mobile Money:**
   - Orange Money: +225 XX XX XX XX
   - MTN Mobile Money: +225 XX XX XX XX
   - Wave: +225 XX XX XX XX

2. **Activer le mode dégradé:**
```bash
# Mettre en pause les transactions non critiques
kubectl set env deployment/prod-orabank-sms-banking \
  MOBILE_MONEY_MAINTENANCE_MODE=true \
  -n orabank-sms-banking-prod
```

3. **Communiquer aux utilisateurs:**
   - Message SMS informatif
   - Mise à jour status page

### Escalade
- Immédiate → Fournisseur Mobile Money + Payments Team
- Si > 30 min → CTO + Direction

---

## 📞 Contacts d'Urgence

| Rôle | Nom | Téléphone | Email |
|------|-----|-----------|-------|
| On-call Backend | Rotation | +225 07 00 00 00 | oncall-backend@orabank.com |
| Lead Backend | AAA | +225 07 00 00 01 | lead-backend@orabank.com |
| DBA | BBB | +225 07 00 00 02 | dba@orabank.com |
| Infrastructure | CCC | +225 07 00 00 03 | infra@orabank.com |
| CISO | DDD | +225 07 00 00 04 | ciso@orabank.com |
| CTO | EEE | +225 07 00 00 05 | cto@orabank.com |

---

## 🛠️ Outils de Diagnostic

- **Grafana:** https://grafana.orabank.com
- **Prometheus:** https://prometheus.orabank.com
- **Kibana Logs:** https://logs.orabank.com
- **Status Page:** https://status.orabank.com

---

*Document maintenu à jour par l'équipe Backend Orabank*
*Dernière mise à jour: $(date +%Y-%m-%d)*
