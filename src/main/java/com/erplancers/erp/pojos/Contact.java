package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.AddressSet;
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

public class Contact extends EntityBase {
    String salutation;
    String name;
    String lastName;
    String email;
    String[] phones;
    String title;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    AddressSet addresses;
    Boolean donotCall;
    String lawfulBasis;
    Boolean isCompany;
    

}
