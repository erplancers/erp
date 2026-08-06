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

import java.time.LocalTime;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table
@ToString
public class BusinessHour extends EntityBase {
    String businessHourName;
    LocalTime startTime;
    LocalTime endTime;
    Boolean active;
    Boolean completed;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Project project;
    String dayofWeek;
    
}
