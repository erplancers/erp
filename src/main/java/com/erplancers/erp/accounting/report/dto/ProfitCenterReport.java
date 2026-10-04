package com.erplancers.erp.accounting.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProfitCenterReport(
        Long profitCenterId,
        String profitCenterCode,
        String profitCenterName,
        LocalDate from,
        LocalDate to,
        List<ProfitCenterLine> lines,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal balance) {
}