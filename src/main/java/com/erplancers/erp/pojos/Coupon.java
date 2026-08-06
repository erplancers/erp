package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.MetadataSet;
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
@ToString
public class Coupon extends EntityBase {
    String couponCode;
    String couponName;
    String couponDescription;
    String couponType;
    Float discount;
    Long couponAmount;
    Long couponPeriod;
    LocalDateTime couponPeriodStart;
    LocalDateTime couponPeriodEnd;
    
}
