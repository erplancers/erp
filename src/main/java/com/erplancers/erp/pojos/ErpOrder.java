package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.CouponSet;
import com.erplancers.erp.pojos.sets.ErpOrderSet;
import com.erplancers.erp.pojos.sets.MetadataSet;
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
public class ErpOrder extends EntityBase {
    
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Invoice invoice;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Customer customer;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contract shippingContract;
    String comment;
    Long totalAmount;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Currency currency;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CouponSet coupons;


}
