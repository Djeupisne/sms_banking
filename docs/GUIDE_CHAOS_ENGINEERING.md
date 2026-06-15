# Tests de Chaos Engineering - Orabank SMS Banking

## Introduction

Le Chaos Engineering est une discipline qui consiste à expérimenter des pannes contrôlées sur un système distribué afin de vérifier sa résilience et sa capacité à récupérer automatiquement.

## Composant ChaosMonkey

Un composant `ChaosMonkey` a été implémenté dans le package `com.orabank.smsbanking.chaos` pour simuler différents scénarios de défaillance.

### Fonctionnalités

1. **Simulation d'échecs temporaires**
   ```java
   chaosMonkey.simulateTemporaryFailure("mobileMoneyService", 20, Duration.ofSeconds(5));
   ```

2. **Simulation de latence élevée**
   ```java
   chaosMonkey.simulateHighLatency("smsGateway", Duration.ofSeconds(2), 10);
   ```

3. **Force ouverture de Circuit Breaker**
   ```java
   chaosMonkey.forceCircuitBreakerOpen("mobileMoneyService");
   ```

4. **Test de résilience complet avec recovery**
   ```java
   chaosMonkey.testResilienceWithRecovery(
       "mobileMoneyService",
       Duration.ofSeconds(10),
       Duration.ofSeconds(15)
   );
   ```

## Exécution des Tests de Chaos

### Test Unitaires

Les tests de chaos sont exécutés automatiquement lors des tests d'intégration:

```bash
mvn test -Dtest=*ChaosTest
```

### Test Manuel en Environnement de Développement

1. Démarrer l'application:
   ```bash
   docker-compose up -d
   ```

2. Exécuter les scénarios de chaos via l'API de test (si exposée) ou via les tests unitaires.

## Scénarios de Test

### Scénario 1: Panne du Service Mobile Money

**Objectif**: Vérifier que le Circuit Breaker s'ouvre correctement et que le fallback est activé.

**Procédure**:
```java
@Test
public void testMobileMoneyServiceFailure() {
    // Simuler 20 échecs consécutifs
    chaosMonkey.simulateTemporaryFailure("mobileMoneyService", 20, Duration.ofSeconds(5));
    
    // Attendre que le circuit breaker s'ouvre
    Thread.sleep(2000);
    
    // Vérifier l'état du circuit breaker
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("mobileMoneyService");
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    
    // Vérifier que le fallback est activé
    // ... assertions sur le comportement de fallback
}
```

**Résultats attendus**:
- ✅ Le circuit breaker passe à l'état OPEN après 5 échecs consécutifs
- ✅ Les appels suivants utilisent immédiatement le fallback
- ✅ Après le waitDuration, le circuit breaker passe à HALF_OPEN
- ✅ Un appel réussi permet de refermer le circuit breaker

### Scénario 2: Latence Excessive du Gateway SMS

**Objectif**: Vérifier que le TimeLimiter timeout correctement.

**Procédure**:
```java
@Test
public void testSmsGatewayTimeout() {
    // Simuler des réponses lentes (2 secondes)
    chaosMonkey.simulateHighLatency("smsGateway", Duration.ofSeconds(2), 5);
    
    // Le TimeLimiter est configuré avec un timeout de 500ms
    // Donc tous les appels devraient timeout
}
```

**Résultats attendus**:
- ✅ Les appels dépassant 500ms sont interrompus
- ✅ Une exception TimeLimiterException est levée
- ✅ Le retry mechanism est déclenché si configuré

### Scénario 3: Recovery Automatique

**Objectif**: Vérifier la capacité du système à récupérer automatiquement.

**Procédure**:
```java
@Test
public void testAutomaticRecovery() {
    chaosMonkey.testResilienceWithRecovery(
        "mobileMoneyService",
        Duration.ofSeconds(10),  // Phase d'échecs
        Duration.ofSeconds(15)   // Phase de récupération
    );
    
    // Attendre la fin du test
    Thread.sleep(30000);
    
    // Vérifier que le circuit breaker est fermé
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("mobileMoneyService");
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
}
```

**Résultats attendus**:
- ✅ Pendant la phase d'échecs, le circuit breaker s'ouvre
- ✅ Pendant la phase de recovery, le circuit breaker teste la réouverture
- ✅ Après des appels réussis, le circuit breaker se referme complètement

## Métriques à Surveiller

Pendant les tests de chaos, surveiller:

1. **État des Circuit Breakers**
   - `resilience4j_circuitbreaker_state`
   
2. **Taux d'échec**
   - `http_server_requests_seconds_count{status=~"5.."}`
   
3. **Latence**
   - `http_server_requests_seconds_bucket`
   
4. **Fallback activés**
   - Métriques custom de fallback

## Intégration CI/CD

Les tests de chaos sont exécutés dans le pipeline GitHub Actions:

```yaml
- name: Run Chaos Tests
  run: mvn test -Dtest=*ChaosTest -P integration
```

## Bonnes Pratiques

1. **Toujours tester en environnement isolé** (jamais en production sans garde-fous)
2. **Avoir un kill switch** pour arrêter immédiatement les tests
3. **Surveiller les métriques** en temps réel pendant les tests
4. **Documenter les résultats** et les apprentissages
5. **Automatiser les scénarios** courants

## Outils Complémentaires

Pour aller plus loin, envisager:

- **Chaos Mesh**: Plateforme de chaos engineering pour Kubernetes
- **Litmus**: Another chaos engineering platform
- **Gremlin**: Solution commerciale de chaos engineering

## Références

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Principles of Chaos Engineering](https://principlesofchaos.org/)
- [Chaos Engineering Book by O'Reilly](https://www.oreilly.com/library/view/chaos-engineering/9781492046899/)
