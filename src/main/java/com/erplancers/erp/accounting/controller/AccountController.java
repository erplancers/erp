package com.erplancers.erp.accounting.controller;

import com.erplancers.erp.accounting.domain.Account;
import com.erplancers.erp.accounting.report.AccountReportService;
import com.erplancers.erp.accounting.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountRepository accountRepository;
    private final AccountReportService accountReportService;

    @GetMapping("/{id}")
    public ResponseEntity<Account> findById(@PathVariable Long id) {
        return accountRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<Account>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<Account> accounts = accountRepository.findAll(PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<?> balance(@PathVariable Long id) {
        return ResponseEntity.ok(accountReportService.balance(id));
    }
}
