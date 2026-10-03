package com.erplancers.erp.web;

import com.erplancers.erp.accounting.repository.AccountRepository;
import com.erplancers.erp.crm.customer.repository.CustomerRepository;
import com.erplancers.erp.finance.repository.InvoiceRepository;
import com.erplancers.erp.inventory.product.repository.ProductRepository;
import com.erplancers.erp.procurement.repository.PurchaseRepository;
import com.erplancers.erp.sales.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PurchaseRepository purchaseRepository;
    private final InvoiceRepository invoiceRepository;

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("accountCount", accountRepository.count());
        model.addAttribute("customerCount", customerRepository.count());
        model.addAttribute("productCount", productRepository.count());
        model.addAttribute("orderCount", orderRepository.count());
        model.addAttribute("purchaseCount", purchaseRepository.count());
        model.addAttribute("invoiceCount", invoiceRepository.count());
        return "dashboard/index";
    }
}
