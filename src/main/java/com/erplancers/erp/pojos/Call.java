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

public class Call extends EntityBase {
    String name;
    Integer duration;
    LocalDateTime timeStart;
    LocalDateTime timeEnd;
    String note;
    String status;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contact contacttoCall;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contact contacttoNote;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Contact callerContact;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Lead lead;
    

}
