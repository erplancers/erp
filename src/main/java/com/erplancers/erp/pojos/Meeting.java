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

public class Meeting extends EntityBase {
    
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ContactSet contacts;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    StoreSet stores;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductSet productstoDiscussion;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    LeadSet leads;

}
