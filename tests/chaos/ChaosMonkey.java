package com.orabank.smsbanking.chaos;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Composant de test de chaos pour simuler des pannes et vérifier la résilience.
 * 
 * Ce composant permet de tester les mécanismes de résilience (Circuit Breaker, Retry, TimeLimiter)
 * en simulant des conditions de défaillance contrôlées.
 */
@Slf4j
@TestComponent
public class ChaosMonkey {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Autowired
    public ChaosMonkey(CircuitBreakerRegistry circuitBreakerRegistry, 
                      TimeLimiterRegistry timeLimiterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
    }

    /**
     * Simule une panne temporaire d'un service externe en forçant des échecs répétés.
     * 
     * @param circuitBreakerName Nom du circuit breaker à tester
     * @param failureCount Nombre d'échecs à simuler
     * @param duration Durée de la simulation
     */
    public void simulateTemporaryFailure(String circuitBreakerName, int failureCount, Duration duration) {
        log.warn("🐵 CHAOS MONKEY: Simulation de {} échecs pour le circuit breaker '{}' pendant {}", 
                failureCount, circuitBreakerName, duration);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        
        int[] failures = {0};
        
        Runnable task = () -> {
            while (failures[0] < failureCount) {
                try {
                    circuitBreaker.executeSupplier(() -> {
                        failures[0]++;
                        log.warn("⚡ CHAOS: Échec simulé {}/{} pour {}", 
                                failures[0], failureCount, circuitBreakerName);
                        throw new RuntimeException("CHAOS: Simulated failure");
                    });
                } catch (Exception e) {
                    // Expected exception
                }
                
                try {
                    Thread.sleep(100); // Petit délai entre les échecs
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            log.info("✅ CHAOS MONKEY: Terminé - {} échecs simulés pour {}", 
                    failures[0], circuitBreakerName);
        };

        scheduler.schedule(task, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Simule une latence excessive pour tester le TimeLimiter.
     * 
     * @param timeLimiterName Nom du time limiter à tester
     * @param delay Délai à simuler
     * @param callCount Nombre d'appels à effectuer
     */
    public void simulateHighLatency(String timeLimiterName, Duration delay, int callCount) {
        log.warn("🐵 CHAOS MONKEY: Simulation de latence élevée ({}) pour '{}'", delay, timeLimiterName);

        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(timeLimiterName);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        for (int i = 0; i < callCount; i++) {
            final int callNumber = i + 1;
            
            Callable<String> slowCall = () -> {
                log.warn("⚡ CHAOS: Appel lent {}/{} avec délai de {}", 
                        callNumber, callCount, delay);
                Thread.sleep(delay.toMillis());
                return "Delayed response";
            };

            scheduler.schedule(() -> {
                try {
                    String result = timeLimiter.executeFutureSupplier(
                        () -> executor.submit(slowCall),
                        Duration.ofMillis(500) // Timeout plus court que le délai
                    ).get();
                    log.info("Résultat: {}", result);
                } catch (Exception e) {
                    log.warn("TIMEOUT détecté comme attendu: {}", e.getMessage());
                }
            }, i * 200, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Force l'ouverture manuelle d'un circuit breaker pour tester le comportement.
     * 
     * @param circuitBreakerName Nom du circuit breaker
     */
    public void forceCircuitBreakerOpen(String circuitBreakerName) {
        log.warn("🐵 CHAOS MONKEY: Force ouverture du circuit breaker '{}'", circuitBreakerName);
        
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);
        
        // Force le circuit breaker en état OPEN en simulant assez d'échecs
        for (int i = 0; i < 10; i++) {
            try {
                circuitBreaker.executeSupplier(() -> {
                    throw new RuntimeException("CHAOS: Forced failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }
        
        log.info("✅ CHAOS MONKEY: Circuit breaker '{}' devrait être OPEN", circuitBreakerName);
        log.info("État actuel: {}", circuitBreaker.getState());
    }

    /**
     * Test de résilience complet avec scénario de recovery.
     * 
     * @param circuitBreakerName Nom du circuit breaker
     * @param failurePhaseDuration Durée de la phase d'échecs
     * @param recoveryPhaseDuration Durée de la phase de récupération
     */
    public void testResilienceWithRecovery(String circuitBreakerName, 
                                          Duration failurePhaseDuration,
                                          Duration recoveryPhaseDuration) {
        log.warn("🐵 CHAOS MONKEY: Test de résilience complet pour '{}'", circuitBreakerName);
        log.warn("  - Phase d'échecs: {}", failurePhaseDuration);
        log.warn("  - Phase de récupération: {}", recoveryPhaseDuration);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);

        // Phase 1: Génération d'échecs
        scheduler.schedule(() -> {
            log.info("▶️  DÉBUT: Phase d'échecs");
            simulateTemporaryFailure(circuitBreakerName, 20, failurePhaseDuration);
        }, 0, TimeUnit.MILLISECONDS);

        // Phase 2: Recovery
        scheduler.schedule(() -> {
            log.info("▶️  DÉBUT: Phase de récupération");
            
            // Simulation d'appels réussis
            Runnable recoveryTask = () -> {
                for (int i = 0; i < 10; i++) {
                    try {
                        circuitBreaker.executeSupplier(() -> {
                            log.info("✅ Appel réussi {}/10 pendant la recovery", i + 1);
                            return "Success";
                        });
                    } catch (Exception e) {
                        log.warn("Échec pendant recovery: {}", e.getMessage());
                    }
                    
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                log.info("✅ CHAOS MONKEY: Test de résilience terminé pour {}", circuitBreakerName);
                log.info("État final du circuit breaker: {}", circuitBreaker.getState());
            };
            
            recoveryTask.run();
        }, failurePhaseDuration.toMillis() + 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * Arrête proprement le scheduler.
     */
    public void shutdown() {
        log.info("Arrêt du Chaos Monkey...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
