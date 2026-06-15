package com.orabank.smsbanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Application principale du système SMS Banking d'Orabank.
 * <p>
 * Permet aux clients d'effectuer des opérations bancaires via des messages SMS :
 * - Consultation de solde
 * - Historique des transactions
 * - Virements vers Mobile Money
 * - Génération d'OTP
 */
@SpringBootApplication
@EnableSpringDataWebSupport
@EnableAsync
public class OrabankSmsBankingApplication {

    /**
     * Point d'entrée principal de l'application.
     *
     * @param args arguments de ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(OrabankSmsBankingApplication.class, args);
    }
}