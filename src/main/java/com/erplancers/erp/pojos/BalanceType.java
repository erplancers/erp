package com.erplancers.erp.pojos;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


public enum BalanceType {

    //TODO [Reverse Engineering] generate columns from DB
    DEBIT,
    CREDIT
}