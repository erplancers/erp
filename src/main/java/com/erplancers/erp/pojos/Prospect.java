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

public class Prospect extends EntityBase {
    
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ContactSet contacts;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProjectSet projects;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Location location;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProjectTaskSet projectTasks;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Lead lead;
}
