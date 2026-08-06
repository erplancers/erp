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

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table

public class Announcement extends EntityBase {
    Boolean read;
    Boolean isActive;
    Boolean topic;
    String title;
    String description;
    String location;
    String message;
    Boolean sent;
    Boolean autoPublish;
    LocalDateTime dateStart;
    LocalDateTime dateEnd;
    LocalDateTime dateCreated;
    
}
