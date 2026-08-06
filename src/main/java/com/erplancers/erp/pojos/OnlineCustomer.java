package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.MetadataSet;
import com.erplancers.erp.pojos.sets.OnlineCustomerSet;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table

public class OnlineCustomer extends EntityBase {
    
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OnlineCustomerSet onlineCustomers;
    LocalDateTime timeOnline;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Store store;
}
