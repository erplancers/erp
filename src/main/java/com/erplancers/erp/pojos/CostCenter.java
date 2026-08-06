package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@Entity
@Table
public class CostCenter extends EntityBase {

    //TODO [Reverse Engineering] generate columns from DB
    @ManyToOne(fetch= FetchType.LAZY)
    private Contact company;

    @Column(nullable=false)
    private String code;

    private String name;

    @ManyToOne(fetch=FetchType.LAZY)
    private CostCenter parent;

    @OneToMany(mappedBy="parent")
    private Set<CostCenter> children;

    private LocalDate validFrom;

    private LocalDate validTo;

    private boolean blocked;
}