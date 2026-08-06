package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table

public class FiscalYear extends EntityBase {


    @ManyToOne(fetch= FetchType.LAZY)
    private Contact company;

    private Integer year;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean closed;


}
