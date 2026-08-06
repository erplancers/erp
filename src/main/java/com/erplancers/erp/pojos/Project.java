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
public class Project extends EntityBase {
    
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductSet products;
    String name;
    String description;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Location location;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Customer customer;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProductDiscountSet productDiscounts;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    AssignmentSet assignments;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProjectTaskSet projectTasks;
}
