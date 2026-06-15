package com.orabank.smsbanking.repository;

import com.orabank.smsbanking.entity.TransactionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    Page<TransactionLog> findByUsername(String username, Pageable pageable);
    Page<TransactionLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<TransactionLog> findByStatus(String status, Pageable pageable);

    @Query("SELECT t.username, COUNT(t) FROM TransactionLog t GROUP BY t.username ORDER BY COUNT(t) DESC")
    List<Object[]> countTransactionsByUser();

    List<TransactionLog> findTop10ByUsernameOrderByCreatedAtDesc(String username);
}