package com.erplancers.erp.accounting.report;

import com.erplancers.erp.accounting.domain.Account;
import com.erplancers.erp.accounting.domain.CostCenter;
import com.erplancers.erp.accounting.domain.FiscalPeriod;
import com.erplancers.erp.accounting.domain.FiscalYear;
import com.erplancers.erp.accounting.domain.ProfitCenter;
import com.erplancers.erp.accounting.repository.AccountRepository;
import com.erplancers.erp.accounting.repository.CostCenterRepository;
import com.erplancers.erp.accounting.repository.FiscalPeriodRepository;
import com.erplancers.erp.accounting.repository.FiscalYearRepository;
import com.erplancers.erp.accounting.repository.ProfitCenterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AccountingReportService {
    private final AccountRepository accountRepository;
    private final FiscalYearRepository fiscalYearRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final CostCenterRepository costCenterRepository;
    private final ProfitCenterRepository profitCenterRepository;

    public AccountingReportService(AccountRepository accountRepository, FiscalYearRepository fiscalYearRepository,
                                   FiscalPeriodRepository fiscalPeriodRepository, CostCenterRepository costCenterRepository,
                                   ProfitCenterRepository profitCenterRepository) {
        this.accountRepository = accountRepository;
        this.fiscalYearRepository = fiscalYearRepository;
        this.fiscalPeriodRepository = fiscalPeriodRepository;
        this.costCenterRepository = costCenterRepository;
        this.profitCenterRepository = profitCenterRepository;
    }

    public List<Account> accounts() {
        return accountRepository.findAll().stream()
                .sorted(Comparator.comparing(Account::getCode, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public List<FiscalYear> fiscalYears() {
        return fiscalYearRepository.findAll().stream()
                .sorted(Comparator.comparing(FiscalYear::getFiscalYear, Comparator.nullsLast(Integer::compareTo)).reversed())
                .toList();
    }

    public List<FiscalPeriod> fiscalPeriods() {
        return fiscalPeriodRepository.findAll().stream()
                .sorted(Comparator.comparing(FiscalPeriod::getPeriodNumber, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    public List<CostCenter> costCenters() {
        return costCenterRepository.findAll().stream()
                .sorted(Comparator.comparing(CostCenter::getCode, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public List<ProfitCenter> profitCenters() {
        return profitCenterRepository.findAll().stream()
                .sorted(Comparator.comparing(ProfitCenter::getCode, Comparator.nullsLast(String::compareTo)))
                .toList();
    }
}