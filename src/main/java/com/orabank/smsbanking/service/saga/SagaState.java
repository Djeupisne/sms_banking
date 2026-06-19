package com.orabank.smsbanking.service.saga;

/**
 * États possibles d'une Saga de transfert Mobile Money.
 */
public enum SagaState {
    /**
     * Saga initialisée, en attente de démarrage.
     */
    INITIATED,

    /**
     * Le compte client a été débité avec succès.
     */
    ACCOUNT_DEBITED,

    /**
     * Le transfert vers Mobile Money a réussi.
     */
    MOBILE_MONEY_TRANSFERRED,

    /**
     * La Saga est complétée avec succès.
     */
    COMPLETED,

    /**
     * Échec pendant le débit du compte (avant l'étape critique).
     */
    FAILED_BEFORE_COMPENSATION,

    /**
     * Échec après le débit, compensation en cours.
     */
    COMPENSATING,

    /**
     * Compensation réussie (remboursement effectué).
     */
    COMPENSATED,

    /**
     * Échec de la compensation (nécessite intervention manuelle).
     */
    COMPENSATION_FAILED,

    /**
     * Saga non trouvée en base.
     */
    NOT_FOUND
}
