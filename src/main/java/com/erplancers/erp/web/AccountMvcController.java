package com.erplancers.erp.web;

import com.erplancers.erp.accounting.domain.Account;
import com.erplancers.erp.accounting.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AccountMvcController {
    private final AccountRepository accountRepository;

    @GetMapping("/accounts")
    public String accounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 5), 100);
        Page<Account> accounts = accountRepository.findAll(PageRequest.of(safePage, safeSize));
        model.addAttribute("accounts", accounts.getContent());
        model.addAttribute("page", accounts.getNumber());
        model.addAttribute("size", accounts.getSize());
        model.addAttribute("totalPages", accounts.getTotalPages());
        model.addAttribute("totalElements", accounts.getTotalElements());
        return "accounting/accounts";
    }
}
