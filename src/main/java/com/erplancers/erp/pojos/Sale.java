package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.StockItemSet;
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

public class Sale extends EntityBase {
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Supplier supplier;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    StockItemSet stockItems;
}
