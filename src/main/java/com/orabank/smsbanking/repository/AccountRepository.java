package com.orabank.smsbanking.repository;

import com.orabank.smsbanking.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // ============================================================
    // MÉTHODES EXISTANTES
    // ============================================================

    Optional<Account> findByClientId(Long clientId);
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findBySystemAccountTrue();
    List<Account> findByAccountType(Account.AccountType accountType);

    // ============================================================
    // NOUVELLES MÉTHODES POUR LA MULTI-COMPTES
    // ============================================================

    /**
     * Récupère tous les comptes (actifs et inactifs) d'un client
     */
    @Query("SELECT a FROM Account a WHERE a.clientId = :clientId")
    List<Account> findAllByClientId(@Param("clientId") Long clientId);

    /**
     * Récupère uniquement les comptes actifs d'un client
     */
    List<Account> findByClientIdAndActiveTrue(Long clientId);

    /**
     * Vérifie si un client a au moins un compte actif
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Account a WHERE a.clientId = :clientId AND a.active = true")
    boolean hasActiveAccount(@Param("clientId") Long clientId);

    /**
     * Vérifie si un client a plusieurs comptes actifs
     */
    @Query("SELECT COUNT(a) > 1 FROM Account a WHERE a.clientId = :clientId AND a.active = true")
    boolean hasMultipleActiveAccounts(@Param("clientId") Long clientId);

    /**
     * Compte le nombre de comptes actifs d'un client
     */
    @Query("SELECT COUNT(a) FROM Account a WHERE a.clientId = :clientId AND a.active = true")
    int countActiveByClientId(@Param("clientId") Long clientId);

    /**
     * Récupère le compte principal d'un client (le premier compte actif)
     */
    @Query("SELECT a FROM Account a WHERE a.clientId = :clientId AND a.active = true ORDER BY a.id ASC LIMIT 1")
    Optional<Account> findPrimaryAccountByClientId(@Param("clientId") Long clientId);

    // ============================================================
    // MÉTHODES POUR LES COMPTES SYSTÈME
    // ============================================================

    @Query("SELECT a FROM Account a WHERE a.systemAccount = true AND a.accountType = :accountType")
    Optional<Account> findSystemAccountByType(@Param("accountType") Account.AccountType accountType);

    @Query("SELECT a FROM Account a WHERE a.systemAccount = true AND a.feeType = :feeType")
    Optional<Account> findSystemAccountByFeeType(@Param("feeType") String feeType);

    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.systemAccount = true AND a.accountType LIKE 'FEE%'")
    BigDecimal getTotalFeesCollected();

    @Query("SELECT a FROM Account a WHERE a.systemAccount = true AND a.active = true")
    List<Account> findAllActiveSystemAccounts();

    // ============================================================
    // MÉTHODES DE VÉRIFICATION
    // ============================================================

    boolean existsByClientId(Long clientId);
}