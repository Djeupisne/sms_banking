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

    //  Méthodes existantes
    Optional<Account> findByClientId(Long clientId);
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findBySystemAccountTrue();
    List<Account> findByAccountType(Account.AccountType accountType);

    //  NOUVELLES MÉTHODES pour récupérer les comptes d'un client
    List<Account> findByClientIdAndActiveTrue(Long clientId);

    @Query("SELECT a FROM Account a WHERE a.clientId = :clientId")
    List<Account> findAllByClientId(@Param("clientId") Long clientId);

    @Query("SELECT a FROM Account a WHERE a.systemAccount = true AND a.accountType = :accountType")
    Optional<Account> findSystemAccountByType(@Param("accountType") Account.AccountType accountType);

    @Query("SELECT a FROM Account a WHERE a.systemAccount = true AND a.feeType = :feeType")
    Optional<Account> findSystemAccountByFeeType(@Param("feeType") String feeType);

    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.systemAccount = true AND a.accountType LIKE 'FEE%'")
    BigDecimal getTotalFeesCollected();

    @Query("SELECT a FROM Account a WHERE a.systemAccount = true AND a.active = true")
    List<Account> findAllActiveSystemAccounts();

    boolean existsByClientId(Long clientId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Account a WHERE a.clientId = :clientId AND a.active = true")
    boolean hasActiveAccount(@Param("clientId") Long clientId);
}