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
@ToString
public class Manufacturer extends EntityBase {
    
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductSet products;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CampaignSet campaigns;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OpportunitySet opportunities;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    StoreSet stores;

}
