package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.CampaignSet;
import com.erplancers.erp.pojos.sets.MetadataSet;
import com.erplancers.erp.pojos.sets.OpportunitySet;
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

public class Lead extends EntityBase {
    
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contact contact;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contact associatedContact;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contact assistantContact;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    OpportunitySet opportunities;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CampaignSet campaigns;
}
