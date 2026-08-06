package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.AccountSet;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table

public class Account extends EntityBase {
    String code;
    String name;
    @ManyToOne
    Account accountParent;
    @ManyToOne
    Contact contact;
    @ManyToOne
    AccountSet accountChildren;
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    private BalanceType balanceType;

    @ManyToOne(fetch = FetchType.LAZY)
    private Currency currency;
    private boolean postingAllowed;

    private boolean reconciliationAccount;

    private boolean blocked;

    private Integer level;

    private Integer sortOrder;


}
