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


public class Alert extends EntityBase {
    String alertName;
    String alertDescription;
    String alertType;
    String alertValue;
    Boolean alertEnabled;
    Boolean alertConfirmed;
    Boolean alertDisabled;
    Boolean isRead;
    String urlRedirect;
    
}
