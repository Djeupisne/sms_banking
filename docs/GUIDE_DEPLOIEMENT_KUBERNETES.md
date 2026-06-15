# Guide de Déploiement Kubernetes - Orabank SMS Banking

## Vue d'ensemble

Ce guide décrit le déploiement de l'application Orabank SMS Banking sur un cluster Kubernetes en utilisant Kustomize pour la gestion des configurations multi-environnements.

## Architecture Kubernetes

```
┌─────────────────────────────────────────────────────┐
│                    Ingress Controller                │
│                  (nginx-ingress)                     │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│              Service (ClusterIP)                     │
│            orabank-sms-banking:80                    │
└─────────────────────┬───────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
   ┌────────┐    ┌────────┐    ┌────────┐
   │  Pod   │    │  Pod   │    │  Pod   │
   │ :8080  │    │ :8080  │    │ :8080  │
   └────────┘    └────────┘    └────────┘
      │             │             │
      ▼             ▼             ▼
┌─────────────────────────────────────────────────────┐
│           External Services                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │PostgreSQL│  │  Redis   │  │MobileMoney│         │
│  └──────────┘  └──────────┘  └──────────┘          │
└─────────────────────────────────────────────────────┘
```

## Prérequis

1. **Cluster Kubernetes** (v1.25+)
   - Minikube, Kind, EKS, GKE, AKS, ou cluster on-premise
   
2. **Kubectl** configuré avec accès au cluster
   ```bash
   kubectl version --client
   ```

3. **Kustomize** (intégré dans kubectl v1.14+)
   ```bash
   kubectl kustomize --help
   ```

4. **Helm** (optionnel, pour les dépendances)
   ```bash
   helm version
   ```

5. **Secrets externes** (à créer avant déploiement)
   ```bash
   kubectl create secret generic orabank-db-secret \
     --from-literal=host=postgres-host \
     --from-literal=username=orabank_user \
     --from-literal=password='secure-password' \
     -n default
   ```

## Structure des Manifestes

```
kubernetes/
├── base/
│   ├── deployment.yaml       # Deployment, Service, HPA, Ingress, PDB
│   └── kustomization.yaml    # Configuration de base
└── overlays/
    ├── dev/
    │   └── kustomization.yaml # Overlay développement
    └── prod/
        └── kustomization.yaml # Overlay production
```

## Déploiement

### Environnement de Développement

1. **Prévisualiser les manifests**
   ```bash
   kubectl kustomize kubernetes/overlays/dev
   ```

2. **Déployer**
   ```bash
   kubectl apply -k kubernetes/overlays/dev
   ```

3. **Vérifier le déploiement**
   ```bash
   kubectl get pods -l app=orabank-sms-banking
   kubectl get svc orabank-sms-banking
   kubectl get hpa
   ```

4. **Accéder à l'application** (port-forward)
   ```bash
   kubectl port-forward svc/dev-orabank-sms-banking 8080:80
   ```

### Environnement de Production

1. **Créer les secrets**
   ```bash
   kubectl create secret generic prod-orabank-db-secret \
     --from-literal=host=prod-postgres.internal \
     --from-literal=username=prod_user \
     --from-literal=password='super-secure-password' \
     -n production
   ```

2. **Déployer**
   ```bash
   kubectl apply -k kubernetes/overlays/prod -n production
   ```

3. **Vérifier**
   ```bash
   kubectl get all -n production -l app=orabank-sms-banking
   ```

## Configuration du Horizontal Pod Autoscaler (HPA)

L'HPA est configuré pour scaler automatiquement:

- **Minimum**: 3 réplicas
- **Maximum**: 10 réplicas
- **CPU target**: 70% d'utilisation
- **Mémoire target**: 80% d'utilisation

### Surveiller le scaling

```bash
kubectl get hpa prod-orabank-sms-banking-hpa -n production --watch
```

## Blue-Green Deployment

Pour effectuer un déploiement blue-green sans interruption de service:

### Étape 1: Déployer la nouvelle version (green)

```bash
# Créer un nouveau déploiement avec la nouvelle version
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orabank-sms-banking-green
spec:
  replicas: 3
  selector:
    matchLabels:
      app: orabank-sms-banking
      version: green
  template:
    metadata:
      labels:
        app: orabank-sms-banking
        version: green
    spec:
      containers:
        - name: sms-banking
          image: orabank/sms-banking:v2.0.0
          # ... reste de la config
EOF
```

### Étape 2: Vérifier la santé de la nouvelle version

```bash
kubectl rollout status deployment/orabank-sms-banking-green
kubectl get pods -l version=green
```

### Étape 3: Basculer le trafic progressivement

```bash
# Modifier le service pour pointer vers green
kubectl patch service orabank-sms-banking \
  -p '{"spec":{"selector":{"version":"green"}}}'
```

### Étape 4: Surveiller et valider

```bash
# Monitorer les logs et métriques
kubectl logs -l version=green -f
```

### Étape 5: Nettoyer l'ancienne version

```bash
kubectl delete deployment orabank-sms-banking-blue
```

## Rollback d'Urgence

En cas de problème après un déploiement:

```bash
# Rollback automatique via Kubernetes
kubectl rollout undo deployment/prod-orabank-sms-banking -n production

# Ou déployer la version précédente
kubectl set image deployment/prod-orabank-sms-banking \
  sms-banking=orabank/sms-banking:v1.0.0 \
  -n production
```

## Monitoring

### Vérifier l'état des pods

```bash
kubectl get pods -l app=orabank-sms-banking -o wide
```

### Voir les logs

```bash
kubectl logs -l app=orabank-sms-banking -f
```

### Accéder aux métriques Prometheus

```bash
kubectl port-forward svc/prometheus 9090:9090
# Puis accéder à http://localhost:9090
```

### Dashboard Grafana

```bash
kubectl port-forward svc/grafana 3000:80
# Puis accéder à http://localhost:3000
# Login: admin / admin
```

## Sécurité

### Network Policies (optionnel)

Pour restreindre le trafic réseau:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: orabank-sms-network-policy
spec:
  podSelector:
    matchLabels:
      app: orabank-sms-banking
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - protocol: TCP
          port: 8080
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              name: database
      ports:
        - protocol: TCP
          port: 5432
```

### RBAC

L'application ne nécessite pas de permissions spéciales RBAC.

## Troubleshooting

### Pod ne démarre pas

```bash
# Voir les événements
kubectl describe pod <pod-name>

# Voir les logs
kubectl logs <pod-name>

# Vérifier les ressources
kubectl top pod <pod-name>
```

### Problèmes de connexion DB

```bash
# Tester la connectivité depuis le pod
kubectl exec -it <pod-name> -- \
  curl -v postgres-host:5432
```

### Circuit Breaker ouvert

Consulter les métriques:
```bash
kubectl port-forward svc/orabank-sms-banking 8080:80
curl http://localhost:8080/actuator/circuitbreakers
```

## Coûts et Optimisation

### Ressources recommandées par environnement

| Environnement | CPU Request | CPU Limit | Memory Request | Memory Limit | Réplicas |
|---------------|-------------|-----------|----------------|--------------|----------|
| Dev           | 250m        | 1000m     | 512Mi          | 1.5Gi        | 2        |
| Staging       | 500m        | 2000m     | 1Gi            | 3Gi          | 3        |
| Production    | 1000m       | 4000m     | 2Gi            | 6Gi          | 5+       |

### Optimisations possibles

1. **Resource Quotas**: Définir des quotas par namespace
2. **LimitRanges**: Définir des limites par défaut
3. **Pod Disruption Budget**: Assurer la disponibilité pendant les maintenances
4. **Affinity/Anti-affinity**: Répartir les pods sur différents nodes

## CI/CD Integration

Exemple de pipeline GitHub Actions:

```yaml
- name: Deploy to Kubernetes
  run: |
    kubectl apply -k kubernetes/overlays/${{ env.ENVIRONMENT }}
    kubectl rollout status deployment/${{ env.DEPLOYMENT_NAME }}
```

## Références

- [Documentation Kubernetes](https://kubernetes.io/docs/)
- [Kustomize Documentation](https://kustomize.io/)
- [Best Practices for Production](https://kubernetes.io/docs/concepts/configuration/overview/)
