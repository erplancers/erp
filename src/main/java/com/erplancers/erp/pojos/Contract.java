package com.erplancers.erp.pojos;

import com.erplancers.erp.pojos.base.EntityBase;
import com.erplancers.erp.pojos.sets.CallSet;
import com.erplancers.erp.pojos.sets.ContractSet;
import com.erplancers.erp.pojos.sets.MetadataSet;

import jakarta.persistence.*;



import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.*;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table

public class Contract extends EntityBase {
    String contractName;
    String referenceValue;
    Long totalContractAmount;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    Currency currency;
    LocalDateTime customerSignedTime;
    LocalDateTime contractSignedTime;
    LocalDateTime dueDate;
    LocalDateTime expiryDate;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    CallSet calls;
    @ManyToOne(cascade = CascadeType.ALL,fetch = FetchType.EAGER)
    ContractSet contracts;
    

}
