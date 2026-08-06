package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.EmailAddressSet;
import com.erplancers.erp.pojos.sets.EmailSet;
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
public class EmailMarketing extends EntityBase {
    
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EmailAddress fromEmailAddress;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EmailAddressSet toEmailAddresses;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Email inbound;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EmailSet outbounds;
}
