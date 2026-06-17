package com.orabank.smsbanking.controller;

import com.orabank.smsbanking.entity.Account;
import com.orabank.smsbanking.entity.Client;
import com.orabank.smsbanking.repository.AccountRepository;
import com.orabank.smsbanking.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;

    /**
     * Récupère tous les comptes d'un client à partir de son numéro de téléphone
     */
    @GetMapping("/{phoneNumber}/accounts")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<Account>> getAccountsByPhone(@PathVariable String phoneNumber) {
        log.info("Récupération des comptes pour le numéro: {}", phoneNumber);

        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new RuntimeException("Client non trouvé avec ce numéro: " + phoneNumber));

        List<Account> accounts = accountRepository.findByClientId(client.getId());

        log.info("{} compte(s) trouvé(s) pour le client {}", accounts.size(), phoneNumber);
        return ResponseEntity.ok(accounts);
    }

    /**
     * Récupère les comptes actifs d'un client
     */
    @GetMapping("/{phoneNumber}/accounts/active")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<Account>> getActiveAccountsByPhone(@PathVariable String phoneNumber) {
        log.info("Récupération des comptes actifs pour le numéro: {}", phoneNumber);

        Client client = clientRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new RuntimeException("Client non trouvé avec ce numéro: " + phoneNumber));

        List<Account> accounts = accountRepository.findByClientIdAndActiveTrue(client.getId());

        log.info("{} compte(s) actif(s) trouvé(s) pour le client {}", accounts.size(), phoneNumber);
        return ResponseEntity.ok(accounts);
    }
}