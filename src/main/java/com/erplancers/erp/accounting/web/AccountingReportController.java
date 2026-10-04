package com.erplancers.erp.accounting.web;

import com.erplancers.erp.accounting.report.AccountReportService;
import com.erplancers.erp.accounting.report.AccountingReportService;
import com.erplancers.erp.accounting.report.CostCenterReportService;
import com.erplancers.erp.accounting.report.FiscalReportService;
import com.erplancers.erp.accounting.report.JournalReportService;
import com.erplancers.erp.accounting.report.ProfitCenterReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Controller
@RequestMapping("/app/accounting/reports")
public class AccountingReportController {
    private final AccountingReportService reports;
    private final AccountReportService accountReports;
    private final JournalReportService journalReports;
    private final FiscalReportService fiscalReports;
    private final CostCenterReportService costCenterReports;
    private final ProfitCenterReportService profitCenterReports;

    public AccountingReportController(AccountingReportService reports, AccountReportService accountReports,
                                      JournalReportService journalReports, FiscalReportService fiscalReports,
                                      CostCenterReportService costCenterReports, ProfitCenterReportService profitCenterReports) {
        this.reports = reports;
        this.accountReports = accountReports;
        this.journalReports = journalReports;
        this.fiscalReports = fiscalReports;
        this.costCenterReports = costCenterReports;
        this.profitCenterReports = profitCenterReports;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("accounts", reports.accounts());
        model.addAttribute("fiscalYears", reports.fiscalYears());
        model.addAttribute("fiscalPeriods", reports.fiscalPeriods());
        model.addAttribute("costCenters", reports.costCenters());
        model.addAttribute("profitCenters", reports.profitCenters());
        return "accounting/reports/index";
    }

    @GetMapping("/accounts/{accountId}")
    public String account(@PathVariable Long accountId, Model model) {
        model.addAttribute("report", accountReports.balance(accountId));
        return "accounting/reports/account";
    }

    @GetMapping("/accounts/{accountId}/ledger")
    public String ledger(@PathVariable Long accountId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         Model model) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        model.addAttribute("report", journalReports.generalLedger(accountId, start, end));
        return "accounting/reports/ledger";
    }

    @GetMapping("/fiscal-years/{fiscalYearId}")
    public String fiscalYear(@PathVariable Long fiscalYearId, Model model) {
        model.addAttribute("accountSummary", fiscalReports.accountSummary(fiscalYearId));
        model.addAttribute("periodSummary", fiscalReports.periodSummary(fiscalYearId));
        return "accounting/reports/fiscal-year";
    }

    @GetMapping("/cost-centers/{costCenterId}")
    public String costCenter(@PathVariable Long costCenterId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             Model model) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        model.addAttribute("report", costCenterReports.generate(costCenterId, start, end));
        return "accounting/reports/cost-center";
    }

    @GetMapping("/profit-centers/{profitCenterId}")
    public String profitCenter(@PathVariable Long profitCenterId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               Model model) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        model.addAttribute("report", profitCenterReports.generate(profitCenterId, start, end));
        return "accounting/reports/profit-center";
    }
}