package com.erplancers.erp.accounting.controller;

import com.erplancers.erp.accounting.domain.Account;
import com.erplancers.erp.accounting.report.AccountReportService;
import com.erplancers.erp.accounting.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class AccountController {
    final AccountRepository accountRepository;
    final AccountReportService accountReportService;
    @GetMapping("/api/accounts/{id}")
    public ResponseEntity<Account> findbyid(@PathVariable Long id){
        Optional<Account> account=accountRepository.findById(id);
        if (account.isPresent())  ResponseEntity.ok(account.get());
        return ResponseEntity.notFound().build();
    }
    @GetMapping("/api/accounts{page}{size}")
    public ResponseEntity<List<Account>> getall(@RequestParam int page,@RequestParam int size){
        List<Account> accounts=accountRepository.findAll(PageRequest.of(page,size)).toList();
        if (accounts.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(accounts);
    }
    public ResponseEntity
}
