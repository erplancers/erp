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

public class Product extends EntityBase {
    
    String productName;
    String productDescription;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    LocationSet location;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CategorySet categories;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    StockStatusSet stockStatuses;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ManufacturerSet manufacturers;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    SupplierSet suppliers;
    Long buyPrice;
    Long sellPrice;
    Long length;
    Long width;
    Long height;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductOptionSet options;

}
