package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.MetadataSet;
import com.erplancers.erp.pojos.sets.ProductOptionSet;
import com.erplancers.erp.pojos.sets.ProductSet;
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
public class OrderOption extends EntityBase {
    
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Product product;
    String option;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductOptionSet productOptions;
    String optionTag;
    Long price;
    Long quantity;
}
