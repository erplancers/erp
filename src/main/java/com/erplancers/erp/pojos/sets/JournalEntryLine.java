package com.erplancers.erp.pojos.sets;

import com.erplancers.erp.pojos.*;
import com.erplancers.erp.pojos.base.EntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(indexes={
        @Index(columnList="journalEntry_id"),
        @Index(columnList="account_id"),
        @Index(columnList="costCenter_id"),
        @Index(columnList="profitCenter_id"),
        @Index(columnList="customer_id"),
        @Index(columnList="supplier_id")
})
public class JournalEntryLine extends EntityBase {


    @ManyToOne(fetch= FetchType.LAZY)
    private JournalEntry journalEntry;

    @ManyToOne(fetch=FetchType.LAZY)
    private Account account;

    @ManyToOne(fetch=FetchType.LAZY)
    private CostCenter costCenter;

    @ManyToOne(fetch=FetchType.LAZY)
    private ProfitCenter profitCenter;

    @ManyToOne(fetch=FetchType.LAZY)
    private Customer customer;

    @ManyToOne(fetch=FetchType.LAZY)
    private Supplier supplier;

    @Column(precision=19,scale=4)
    private BigDecimal debit;

    @Column(precision=19,scale=4)
    private BigDecimal credit;

    @Column(precision=19,scale=4)
    private BigDecimal amountInLocalCurrency;

    private String text;

    private Integer lineNumber;
}
