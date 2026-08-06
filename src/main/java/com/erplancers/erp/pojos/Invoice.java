package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.ContactSet;
import com.erplancers.erp.pojos.sets.MetadataSet;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

@Entity
@NoArgsConstructor
@Getter
@Setter

public class Invoice extends EntityBase {
    
    String invoiceName;
    String invoiceDescription;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ContactSet billingContacts;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ContactSet shippingContacts;
    @ManyToOne

    Currency currency;
    Long amount;


}
