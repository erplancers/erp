package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.CustomerGroupSet;
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
public class TaxRate extends EntityBase {
    
    String description;
    float rate;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Zone zone;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Location location;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CustomerGroupSet customerGroups;

}
