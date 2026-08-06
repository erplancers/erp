package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.MetadataSet;
import com.erplancers.erp.pojos.sets.ProspectSet;
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

public class ProspectList extends EntityBase {
    
    String name;
    String description;
    String status;
    String type;
    String category;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProspectSet prospects;
}
