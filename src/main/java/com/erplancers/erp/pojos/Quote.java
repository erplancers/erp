package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table
@ToString
public class Quote extends EntityBase {
    
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contact contactBilling;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contact contactShipping;
    Long numberQuote;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OpportunitySet opportunities;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SaleSet sales;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Currency currency;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    InvoiceSet invoices;

}
