package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.MetadataSet;


import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.*;


import lombok.*;


@Entity
@NoArgsConstructor
@Getter
@Setter
@Table

public class Action extends EntityBase {
    String actionName;
    String actionDescription;
    String actionType;
    String parameters;
    String actionValue;
    
}
