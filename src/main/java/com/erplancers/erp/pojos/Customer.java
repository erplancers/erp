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

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table

public class Customer extends EntityBase {
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contact contact;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CustomerGroup customerGroup;
    
}
