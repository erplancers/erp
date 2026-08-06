package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@Table(indexes={
        @Index(columnList="fiscalYear_id,periodNumber",unique=true),
        @Index(columnList="startDate"),
        @Index(columnList="endDate")
})
@Getter
@Setter

public class FiscalPeriod extends EntityBase {



    @ManyToOne(fetch= FetchType.LAZY)
    private FiscalYear fiscalYear;

    private Integer periodNumber;

    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    private boolean closed;

    private boolean adjustmentPeriod;
}