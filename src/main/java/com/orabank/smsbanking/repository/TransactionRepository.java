package com.orabank.smsbanking.repository;

import com.orabank.smsbanking.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // ============================================================
    // MÉTHODES EXISTANTES
    // ============================================================

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

    // ============================================================
    // NOUVELLES MÉTHODES AVEC LIMIT DYNAMIQUE
    // ============================================================

    /**
     * Récupère les N dernières transactions d'un compte (limit dynamique)
     *
     * @param accountId l'ID du compte
     * @param limit le nombre maximum de transactions à récupérer
     * @return la liste des transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId ORDER BY t.createdAt DESC")
    List<Transaction> findTopByAccountIdOrderByCreatedAtDesc(
            @Param("accountId") Long accountId,
            @Param("limit") int limit);

    /**
     * Récupère les transactions d'un compte avec pagination
     *
     * @param accountId l'ID du compte
     * @param limit le nombre maximum de transactions à récupérer
     * @param offset l'offset pour la pagination
     * @return la liste des transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId ORDER BY t.createdAt DESC")
    List<Transaction> findTransactionsByAccountIdWithPagination(
            @Param("accountId") Long accountId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * Récupère les transactions d'un compte par type
     *
     * @param accountId l'ID du compte
     * @param type le type de transaction (CREDIT, DEBIT, etc.)
     * @param limit le nombre maximum de transactions à récupérer
     * @return la liste des transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId AND t.type = :type ORDER BY t.createdAt DESC")
    List<Transaction> findTopByAccountIdAndTypeOrderByCreatedAtDesc(
            @Param("accountId") Long accountId,
            @Param("type") String type,
            @Param("limit") int limit);

    /**
     * Récupère les transactions d'un compte sur une période donnée
     *
     * @param accountId l'ID du compte
     * @param startDate la date de début
     * @param endDate la date de fin
     * @param limit le nombre maximum de transactions à récupérer
     * @return la liste des transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId " +
            "AND t.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findTransactionsByAccountIdAndDateRange(
            @Param("accountId") Long accountId,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            @Param("limit") int limit);

    /**
     * Compte le nombre total de transactions pour un compte
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.accountId = :accountId")
    long countByAccountId(@Param("accountId") Long accountId);

    /**
     * Récupère les transactions par statut
     */
    @Query("SELECT t FROM Transaction t WHERE t.status = :status ORDER BY t.createdAt DESC")
    List<Transaction> findTopByStatusOrderByCreatedAtDesc(
            @Param("status") com.orabank.smsbanking.entity.enums.TransactionStatus status,
            @Param("limit") int limit);

    /**
     * Récupère les dernières transactions pour un client (tous ses comptes)
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId IN " +
            "(SELECT a.id FROM Account a WHERE a.clientId = :clientId) " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findTopByClientIdOrderByCreatedAtDesc(
            @Param("clientId") Long clientId,
            @Param("limit") int limit);

    /**
     * Récupère le montant total des transactions d'un type pour un compte
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.accountId = :accountId AND t.type = :type AND t.status = 'COMPLETED'")
    BigDecimal getTotalAmountByAccountIdAndType(
            @Param("accountId") Long accountId,
            @Param("type") String type);

    /**
     * Récupère les transactions par compte et statut
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId AND t.status = :status ORDER BY t.createdAt DESC")
    List<Transaction> findTransactionsByAccountIdAndStatus(
            @Param("accountId") Long accountId,
            @Param("status") com.orabank.smsbanking.entity.enums.TransactionStatus status,
            @Param("limit") int limit);

    /**
     * Récupère les transactions par type avec pagination
     */
    @Query("SELECT t FROM Transaction t WHERE t.type = :type ORDER BY t.createdAt DESC")
    List<Transaction> findTransactionsByTypeWithPagination(
            @Param("type") String type,
            @Param("limit") int limit,
            @Param("offset") int offset);
}