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

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table

public class Event extends EntityBase {
    
    String eventName;
    LocalDateTime timeStart;
    LocalDateTime timeEnd;
    Long budget;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Currency currency;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    EventLocationSet locations;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ContactSet contacts;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    LeadSet leads;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ProspectSet prospects;
}
