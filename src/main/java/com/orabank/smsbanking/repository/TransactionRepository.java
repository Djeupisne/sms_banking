package com.orabank.smsbanking.repository;

import com.orabank.smsbanking.entity.Transaction;
import com.orabank.smsbanking.entity.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    
    // MÉTHODES EXISTANTES
    

    /**
     * Récupère les 5 dernières transactions d'un compte
     */
    List<Transaction> findTop5ByAccountIdOrderByCreatedAtDesc(Long accountId);

    /**
     * Récupère le total des frais collectés
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'CREDIT_FEES'")
    BigDecimal findTotalFeesCollected();

    /**
     * Récupère les transactions par référence
     */
    List<Transaction> findByReference(String reference);

    /**
     * Récupère les transactions par statut
     */
    List<Transaction> findByStatus(TransactionStatus status);

    
    // MÉTHODES AVEC LIMIT DYNAMIQUE (CORRECTES)
    

    /**
     * Récupère les N dernières transactions d'un compte (limit dynamique)
     * Utilisation de nativeQuery car LIMIT n'est pas supporté en JPQL standard
     */
    @Query(value = "SELECT * FROM transactions WHERE account_id = :accountId ORDER BY created_at DESC LIMIT :limit",
            nativeQuery = true)
    List<Transaction> findTopByAccountIdOrderByCreatedAtDesc(
            @Param("accountId") Long accountId,
            @Param("limit") int limit);

    /**
     * Récupère les transactions d'un compte par type avec limit
     */
    @Query(value = "SELECT * FROM transactions WHERE account_id = :accountId AND type = :type ORDER BY created_at DESC LIMIT :limit",
            nativeQuery = true)
    List<Transaction> findTopByAccountIdAndTypeOrderByCreatedAtDesc(
            @Param("accountId") Long accountId,
            @Param("type") String type,
            @Param("limit") int limit);

    /**
     * Récupère les transactions d'un compte avec pagination
     */
    @Query(value = "SELECT * FROM transactions WHERE account_id = :accountId ORDER BY created_at DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Transaction> findTransactionsByAccountIdWithPagination(
            @Param("accountId") Long accountId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * Récupère les transactions d'un compte sur une période donnée
     */
    @Query(value = "SELECT * FROM transactions WHERE account_id = :accountId " +
            "AND created_at BETWEEN :startDate AND :endDate " +
            "ORDER BY created_at DESC LIMIT :limit",
            nativeQuery = true)
    List<Transaction> findTransactionsByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("limit") int limit);

    /**
     * Récupère les transactions par statut avec limit
     */
    @Query(value = "SELECT * FROM transactions WHERE status = :status ORDER BY created_at DESC LIMIT :limit",
            nativeQuery = true)
    List<Transaction> findTopByStatusOrderByCreatedAtDesc(
            @Param("status") String status,
            @Param("limit") int limit);

    /**
     * Récupère les dernières transactions pour un client (tous ses comptes)
     */
    @Query(value = "SELECT t.* FROM transactions t " +
            "INNER JOIN accounts a ON t.account_id = a.id " +
            "WHERE a.client_id = :clientId " +
            "ORDER BY t.created_at DESC LIMIT :limit",
            nativeQuery = true)
    List<Transaction> findTopByClientIdOrderByCreatedAtDesc(
            @Param("clientId") Long clientId,
            @Param("limit") int limit);

    /**
     * Récupère les transactions d'un compte avec un statut spécifique
     */
    @Query(value = "SELECT * FROM transactions WHERE account_id = :accountId AND status = :status ORDER BY created_at DESC LIMIT :limit",
            nativeQuery = true)
    List<Transaction> findTransactionsByAccountIdAndStatus(
            @Param("accountId") Long accountId,
            @Param("status") String status,
            @Param("limit") int limit);

    /**
     * Récupère les transactions par type avec pagination
     */
    @Query(value = "SELECT * FROM transactions WHERE type = :type ORDER BY created_at DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Transaction> findTransactionsByTypeWithPagination(
            @Param("type") String type,
            @Param("limit") int limit,
            @Param("offset") int offset);

    
    // MÉTHODES DE COMPTAGE
    

    /**
     * Compte le nombre total de transactions pour un compte
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.accountId = :accountId")
    long countByAccountId(@Param("accountId") Long accountId);

    /**
     * Compte le nombre de transactions par type pour un compte
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.accountId = :accountId AND t.type = :type")
    long countByAccountIdAndType(@Param("accountId") Long accountId, @Param("type") String type);

    /**
     * Compte le nombre de transactions par statut pour un compte
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.accountId = :accountId AND t.status = :status")
    long countByAccountIdAndStatus(@Param("accountId") Long accountId, @Param("status") TransactionStatus status);

    
    // MÉTHODES DE SOMME
    

    /**
     * Récupère le montant total des transactions d'un type pour un compte
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.accountId = :accountId AND t.type = :type AND t.status = 'COMPLETED'")
    BigDecimal getTotalAmountByAccountIdAndType(
            @Param("accountId") Long accountId,
            @Param("type") String type);

    /**
     * Récupère le montant total des transactions pour un compte sur une période
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.accountId = :accountId AND t.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalAmountByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}