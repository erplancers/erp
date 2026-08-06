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
@ToString
public class Assignment extends EntityBase {
    String assignmentName;

    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Folder folder;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Document document;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Project project;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Note note;
    
}
