package com.erplancers.erp.accounting.report;

import com.erplancers.erp.accounting.domain.JournalEntryLine;
import com.erplancers.erp.accounting.domain.ProfitCenter;
import com.erplancers.erp.accounting.repository.JournalEntryLineRepository;
import com.erplancers.erp.accounting.repository.ProfitCenterRepository;
import com.erplancers.erp.accounting.report.dto.ProfitCenterLine;
import com.erplancers.erp.accounting.report.dto.ProfitCenterReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProfitCenterReportService {
    private final JournalEntryLineRepository lineRepository;
    private final ProfitCenterRepository profitCenterRepository;

    public ProfitCenterReportService(JournalEntryLineRepository lineRepository, ProfitCenterRepository profitCenterRepository) {
        this.lineRepository = lineRepository;
        this.profitCenterRepository = profitCenterRepository;
    }

    public ProfitCenterReport generate(Long profitCenterId, LocalDate from, LocalDate to) {
        ProfitCenter profitCenter = profitCenterRepository.findById(profitCenterId)
                .orElseThrow(() -> new IllegalArgumentException("Profit center not found: " + profitCenterId));
        List<ProfitCenterLine> lines = lineRepository.findByProfitCenterAndEntryDateBetween(profitCenter, from, to)
                .stream().map(this::toLine).toList();
        BigDecimal totalDebit = lines.stream().map(ProfitCenterLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream().map(ProfitCenterLine::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProfitCenterReport(profitCenter.getId(), profitCenter.getCode(), profitCenter.getName(),
                from, to, lines, totalDebit, totalCredit, totalDebit.subtract(totalCredit));
    }

    private ProfitCenterLine toLine(JournalEntryLine line) {
        BigDecimal debit = safe(line.getDebit());
        BigDecimal credit = safe(line.getCredit());
        return new ProfitCenterLine(line.getJournalEntry().getId(), line.getJournalEntry().getEntryId(),
                line.getJournalEntry().getEntryDate(), line.getAccount().getId(), line.getAccount().getCode(),
                line.getAccount().getName(), debit, credit, debit.subtract(credit), line.getDescription());
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}